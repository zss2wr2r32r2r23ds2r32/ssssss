package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.gui.MenuHolder;
import com.shardedmc.lobbycore.gui.MenuType;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import com.shardedmc.lobbycore.util.NbsDurationParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class MusicModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private BukkitTask actionBarTask;
    private final Map<UUID, BukkitTask> advanceTasks = new HashMap<>();
    private final Map<Integer, String> mainSlotActions = new HashMap<>();
    private final Map<Integer, String> playlistSlotActions = new HashMap<>();
    private final Map<String, String> songDisplayNames = new HashMap<>();
    private final Map<String, Integer> songDurations = new HashMap<>();
    private final Map<String, ConfigurationSection> songSections = new LinkedHashMap<>();

    @Override
    public String getId() {
        return "music";
    }

    @Override
    public String getDisplayName() {
        return "Music";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        reloadSongCache();
        loadNbsDurations();
        loadCalibratedDurations();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerSongEndListener();
        startActionBarTask();
    }

    private void registerSongEndListener() {
        if (!config.getBoolean("playlist-queue.auto-advance", true)) {
            return;
        }

        String[] eventClasses = {
                "io.papermc.paper.event.player.PlayerReceiveMessageEvent",
                "com.destroystokyo.paper.event.player.PlayerReceiveMessageEvent"
        };
        for (String className : eventClasses) {
            if (registerMessageEvent(className)) {
                plugin.getLogger().info("Playlist auto-advance hooked into " + className);
                return;
            }
        }

        if (registerMessageEvent("io.papermc.paper.event.player.AsyncChatEvent")) {
            plugin.getLogger().info("Playlist auto-advance hooked into AsyncChatEvent (fallback)");
            return;
        }

        plugin.getLogger().info("Playlist auto-advance using song duration timer (message hook unavailable)");
    }

    private boolean registerMessageEvent(String className) {
        try {
            Class<? extends Event> eventClass = Class.forName(className).asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> {
                try {
                    Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    Component message = extractMessageComponent(event);
                    if (message == null) {
                        return;
                    }
                    String plain = PlainTextComponentSerializer.plainText().serialize(message);
                    tryHandleSongEnd(player, plain);
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Playlist auto-advance error: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, executor, plugin, true);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Component extractMessageComponent(Object event) throws ReflectiveOperationException {
        try {
            return (Component) event.getClass().getMethod("message").invoke(event);
        } catch (NoSuchMethodException ignored) {
            return (Component) event.getClass().getMethod("getMessage").invoke(event);
        }
    }

    private void tryHandleSongEnd(Player player, String plain) {
        UUID uuid = player.getUniqueId();
        if (!plugin.getPlaylistManager().isPlaylistPlayback(uuid)) {
            return;
        }
        if (plugin.getPlaylistManager().getQueue(uuid).isEmpty()) {
            return;
        }
        if (plugin.getPlaylistManager().isManualSkipCooldown(uuid)) {
            return;
        }

        String upper = plain.toUpperCase(Locale.ROOT);
        List<String> triggers = config.getStringList("playlist-queue.end-triggers");
        if (triggers.isEmpty()) {
            triggers = List.of("SONG STOPPED", "SONG ENDED", "STOPPED.");
        }

        boolean matched = false;
        for (String trigger : triggers) {
            if (upper.contains(trigger.toUpperCase(Locale.ROOT))) {
                matched = true;
                break;
            }
        }
        if (!matched) {
            return;
        }

        long delay = config.getLong("playlist-queue.play-delay-ticks", 10L);
        cancelAdvanceTask(uuid);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!plugin.getPlaylistManager().isPlaylistPlayback(uuid)) {
                return;
            }
            playNextFromQueue(player);
        }, delay);
    }

    private void cancelAdvanceTask(UUID uuid) {
        BukkitTask task = advanceTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void scheduleAdvanceAfterSong(Player player, String songId) {
        if (!config.getBoolean("playlist-queue.auto-advance", true)) {
            return;
        }
        String mode = config.getString("playlist-queue.advance-mode", "both").toLowerCase(Locale.ROOT);
        if ("message".equals(mode)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (!plugin.getPlaylistManager().isPlaylistPlayback(uuid)) {
            return;
        }
        if (plugin.getPlaylistManager().getQueue(uuid).isEmpty()) {
            return;
        }

        cancelAdvanceTask(uuid);
        int duration = getSongDurationSeconds(songId);
        int buffer = config.getInt("playlist-queue.duration-buffer-seconds", 2);
        long ticks = (duration + buffer) * 20L;

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            if (!plugin.getPlaylistManager().isPlaylistPlayback(uuid)) {
                return;
            }
            if (plugin.getPlaylistManager().getQueue(uuid).isEmpty()) {
                return;
            }
            playNextFromQueue(player);
        }, ticks);
        advanceTasks.put(uuid, task);
    }

    private int getSongDurationSeconds(String songId) {
        return songDurations.getOrDefault(songId,
                config.getInt("playlist-queue.default-duration-seconds", 200));
    }

    @Override
    public void disable() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
            actionBarTask = null;
        }
        for (BukkitTask task : advanceTasks.values()) {
            task.cancel();
        }
        advanceTasks.clear();
        mainSlotActions.clear();
        playlistSlotActions.clear();
        songDisplayNames.clear();
        songDurations.clear();
        songSections.clear();
        HandlerList.unregisterAll(this);
    }

    private void startActionBarTask() {
        if (!config.getBoolean("action-bar.enabled", true)) {
            return;
        }
        long interval = config.getLong("action-bar.update-interval-ticks", 40L);
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (plugin.getPlaylistManager().hasPlaylist(player.getUniqueId())) {
                    updateUpNextActionBar(player);
                }
            }
        }, interval, interval);
    }

    private void reloadSongCache() {
        songDisplayNames.clear();
        songDurations.clear();
        songSections.clear();
        int defaultDuration = config.getInt("playlist-queue.default-duration-seconds", 200);
        ConfigurationSection songs = config.getConfigurationSection("songs");
        if (songs == null) {
            return;
        }
        for (String key : songs.getKeys(false)) {
            ConfigurationSection song = songs.getConfigurationSection(key);
            if (song == null) {
                continue;
            }
            String songId = song.getString("song", key);
            songSections.put(key, song);
            songDisplayNames.put(songId, MessageUtil.plainText(song.getString("name", key)));
            songDurations.put(songId, song.getInt("duration-seconds", defaultDuration));
        }
    }

    private static final Map<String, String> NBS_SONG_ALIASES = Map.of(
            "DJGOTUSFALLININLOVE", "DJGOTUSFALLIN'INLOVE",
            "MAJORLAZER-COLDWATER", "MAJORLAZER-COLDWATER(FEAT.JUSTINBIEBER_M¥)EASY"
    );

    private void loadCalibratedDurations() {
        File file = new File(plugin.getDataFolder(), "song-durations.yml");
        if (!file.exists()) {
            plugin.saveResource("song-durations.yml", false);
        }
        org.bukkit.configuration.file.YamlConfiguration durations =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        InputStream defaults = plugin.getResource("song-durations.yml");
        if (defaults != null) {
            durations.setDefaults(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaults, java.nio.charset.StandardCharsets.UTF_8)));
        }
        for (String songId : durations.getKeys(false)) {
            songDurations.put(songId, durations.getInt(songId));
        }
    }

    private void loadNbsDurations() {
        File songsFolder = new File(plugin.getDataFolder(), "songs");
        if (!songsFolder.exists()) {
            songsFolder.mkdirs();
            extractBundledSongs(songsFolder);
        }

        File[] files = songsFolder.listFiles((dir, name) ->
                name.endsWith(".nbs") || name.endsWith(".gnbs"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String baseName = file.getName();
            int dot = baseName.lastIndexOf('.');
            String songId = dot > 0 ? baseName.substring(0, dot) : baseName;
            songId = NBS_SONG_ALIASES.getOrDefault(songId, songId);
            try {
                int seconds = NbsDurationParser.parseDurationSeconds(file.toPath());
                if (seconds > 0 && !songDurations.containsKey(songId)) {
                    songDurations.put(songId, seconds);
                    plugin.getLogger().info("Loaded NBS duration for " + songId + ": " + seconds + "s");
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not parse NBS duration for " + file.getName() + ": " + ex.getMessage());
            }
        }
    }

    private void extractBundledSongs(File targetFolder) {
        String[] bundled = {
                "ALANWALKER-FADE.nbs", "BEATIT.nbs", "BILLIEJEAN.nbs", "CALLMEMAYBE.nbs",
                "COUNTINGSTAS.nbs", "DJGOTUSFALLININLOVE.nbs", "MAJORLAZER-COLDWATER.nbs",
                "TAKEONME.nbs", "WAITINGFORLOVE.nbs", "FEELGOODINC.nbs", "HIGHEST-IN-THE-ROOM.nbs",
                "IGOTAFEELING.nbs", "LUCID-DREAMS.nbs", "RANSOM.nbs", "SHOOTINGSTARS.nbs",
                "THRILLER.nbs", "WAKEMEUPINSIDE.nbs", "YMCA.nbs", "ITDOKAPILLINIBIZA.gnbs"
        };
        for (String name : bundled) {
            if (plugin.getResource("songs/" + name) == null) {
                continue;
            }
            File out = new File(targetFolder, name);
            if (out.exists()) {
                continue;
            }
            try (InputStream in = plugin.getResource("songs/" + name)) {
                if (in != null) {
                    Files.copy(in, out.toPath());
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not extract bundled song " + name);
            }
        }
    }

    public void openMenu(Player player) {
        openMainMenu(player);
    }

    private void openMainMenu(Player player) {
        int rows = Math.min(6, Math.max(1, config.getInt("gui.rows", 6)));
        MenuHolder holder = new MenuHolder(MenuType.MUSIC_MAIN);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                MessageUtil.component(config.getString("gui.title", "Music Player")));
        holder.setInventory(inventory);
        mainSlotActions.clear();

        ConfigurationSection songs = config.getConfigurationSection("songs");
        if (songs != null) {
            for (String key : songs.getKeys(false)) {
                ConfigurationSection song = songs.getConfigurationSection(key);
                if (song == null) {
                    continue;
                }
                int slot = song.getInt("slot");
                inventory.setItem(slot, ItemBuilder.fromConfig(song, player));
                mainSlotActions.put(slot, "play:" + song.getString("song", key));
            }
        }

        ConfigurationSection controls = config.getConfigurationSection("controls");
        if (controls != null) {
            for (String key : controls.getKeys(false)) {
                ConfigurationSection control = controls.getConfigurationSection(key);
                if (control == null) {
                    continue;
                }
                int slot = control.getInt("slot");
                inventory.setItem(slot, ItemBuilder.fromConfig(control, player));
                mainSlotActions.put(slot, control.getString("action", key));
            }
        }

        fillEmpty(inventory, player);
        player.openInventory(inventory);
    }

    private void openPlaylistMenu(Player player) {
        openPlaylistMenu(player, true);
    }

    private void openPlaylistMenu(Player player, boolean fresh) {
        if (fresh) {
            plugin.getPlaylistManager().startDraft(player.getUniqueId());
        }

        int rows = Math.min(6, Math.max(1, config.getInt("playlist-gui.rows", 6)));
        MenuHolder holder = new MenuHolder(MenuType.MUSIC_PLAYLIST);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                MessageUtil.component(config.getString("playlist-gui.title", "&#FF0072Playlist")));
        holder.setInventory(inventory);
        playlistSlotActions.clear();

        int confirmSlot = config.getInt("playlist-gui.confirm-slot", 49);
        int playSlot = config.getInt("playlist-gui.play-slot", 48);
        int summarySlot = config.getInt("playlist-gui.summary-slot", 50);
        Set<Integer> reserved = new HashSet<>(Set.of(confirmSlot, playSlot, summarySlot));

        int slot = 0;
        for (Map.Entry<String, ConfigurationSection> entry : songSections.entrySet()) {
            while (reserved.contains(slot)) {
                slot++;
            }
            if (slot >= inventory.getSize()) {
                break;
            }
            ConfigurationSection song = entry.getValue();
            String songId = song.getString("song", entry.getKey());
            inventory.setItem(slot, buildPlaylistItem(player, song, songId));
            playlistSlotActions.put(slot, songId);
            slot++;
        }

        inventory.setItem(playSlot, buildPlayPlaylistItem(player));
        playlistSlotActions.put(playSlot, "play-playlist");

        ConfigurationSection confirmSection = config.getConfigurationSection("playlist-gui.confirm");
        if (confirmSection != null) {
            inventory.setItem(confirmSlot, ItemBuilder.fromConfig(confirmSection, player));
        } else {
            inventory.setItem(confirmSlot, ItemBuilder.of(Material.LIME_CONCRETE)
                    .name(MessageUtil.format(config.getString("playlist-gui.confirm-name", "&#94FF00&lCONFIRM"), player))
                    .lore(MessageUtil.formatLore(config.getStringList("playlist-gui.confirm-lore"), player))
                    .build());
        }
        playlistSlotActions.put(confirmSlot, "confirm");

        populateSummaryBook(player, inventory, summarySlot);

        fillEmpty(inventory, player);
        player.openInventory(inventory);
    }

    private ItemStack buildPlayPlaylistItem(Player player) {
        ConfigurationSection section = config.getConfigurationSection("playlist-gui.play");
        if (section != null) {
            return ItemBuilder.fromConfig(section, player);
        }
        return ItemBuilder.of(Material.NOTE_BLOCK)
                .name(MessageUtil.format(config.getString("playlist-gui.play-name", "&#FF0072&lPLAY PLAYLIST"), player))
                .lore(MessageUtil.formatLore(config.getStringList("playlist-gui.play-lore"), player))
                .build();
    }

    private void populateSummaryBook(Player player, Inventory inventory, int summarySlot) {
        if (summarySlot >= inventory.getSize()) {
            return;
        }

        List<String> draft = plugin.getPlaylistManager().getDraft(player.getUniqueId());
        if (draft.isEmpty()) {
            inventory.setItem(summarySlot, ItemBuilder.of(Material.BOOK)
                    .name(MessageUtil.format(config.getString("playlist-gui.summary-empty-name", "&7No songs selected"), player))
                    .lore(MessageUtil.formatLore(config.getStringList("playlist-gui.summary-empty-lore"), player))
                    .build());
            return;
        }

        Material material = Material.matchMaterial(config.getString("playlist-gui.summary-material", "BOOK"));
        if (material == null) {
            material = Material.BOOK;
        }

        String lineFormat = config.getString("playlist-gui.summary-line-format", "&#FF0072#%number% &f%song%");
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (int i = 0; i < draft.size(); i++) {
            String songId = draft.get(i);
            String display = songDisplayNames.getOrDefault(songId, songId);
            lore.add(lineFormat
                    .replace("%number%", String.valueOf(i + 1))
                    .replace("%song%", display));
        }
        lore.add("");

        inventory.setItem(summarySlot, ItemBuilder.of(material)
                .name(MessageUtil.format(config.getString("playlist-gui.summary-name", "&#FF0072&lYOUR PLAYLIST"), player))
                .lore(MessageUtil.formatLore(lore, player))
                .build());
    }

    private ItemStack buildPlaylistItem(Player player, ConfigurationSection song, String songId) {
        UUID uuid = player.getUniqueId();
        int position = plugin.getPlaylistManager().getDraftPosition(uuid, songId);
        if (position > 0) {
            String displayName = MessageUtil.plainText(song.getString("name", songId));
            List<String> lore = new ArrayList<>(config.getStringList("playlist-gui.selected-lore"));
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, lore.get(i)
                        .replace("%song%", displayName)
                        .replace("%song-name%", displayName)
                        .replace("%number%", String.valueOf(position)));
            }
            Material material = Material.matchMaterial(config.getString("playlist-gui.selected-material", "LIME_STAINED_GLASS_PANE"));
            if (material == null) {
                material = Material.LIME_STAINED_GLASS_PANE;
            }
            String nameTemplate = config.getString("playlist-gui.selected-name", "&#94FF00Selected &7(#%number%)");
            return ItemBuilder.of(material)
                    .name(MessageUtil.format(nameTemplate.replace("%number%", String.valueOf(position)), player))
                    .lore(MessageUtil.formatLore(lore, player))
                    .build();
        }
        return ItemBuilder.fromConfig(song, player);
    }

    private void fillEmpty(Inventory inventory, Player player) {
        if (config.isConfigurationSection("filler")) {
            ItemStack filler = ItemBuilder.fromConfig(config.getConfigurationSection("filler"), player);
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.getPlaylistManager().hasPlaylist(event.getPlayer().getUniqueId())) {
                updateUpNextActionBar(event.getPlayer());
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelAdvanceTask(uuid);
        plugin.getPlaylistManager().clearDraft(uuid);
        plugin.getPlaylistManager().endPlaylistPlayback(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.getType() != MenuType.MUSIC_MAIN && holder.getType() != MenuType.MUSIC_PLAYLIST) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() != top) {
            return;
        }

        int slot = event.getSlot();
        if (holder.getType() == MenuType.MUSIC_MAIN) {
            handleMainClick(player, slot);
        } else {
            handlePlaylistClick(player, slot);
        }
    }

    private void handleMainClick(Player player, int slot) {
        String action = mainSlotActions.get(slot);
        if (action == null) {
            return;
        }

        if ("playlist".equals(action)) {
            openPlaylistMenu(player);
            return;
        }

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> executeAction(player, action));
    }

    private void handlePlaylistClick(Player player, int slot) {
        String action = playlistSlotActions.get(slot);
        if (action == null) {
            return;
        }

        if ("confirm".equals(action)) {
            confirmPlaylist(player);
            return;
        }

        if ("play-playlist".equals(action)) {
            playSavedPlaylist(player);
            return;
        }

        plugin.getPlaylistManager().toggleDraftSong(player.getUniqueId(), action);
        openPlaylistMenu(player, false);
    }

    private void confirmPlaylist(Player player) {
        List<String> draft = new ArrayList<>(plugin.getPlaylistManager().getDraft(player.getUniqueId()));
        if (draft.isEmpty()) {
            MessageUtil.sendFormatted(player, config.getString("playlist-gui.empty-message",
                    "%prefix% &#FF2727Select at least one song for your playlist."));
            return;
        }

        plugin.getPlaylistManager().savePlaylist(player.getUniqueId(), draft);
        plugin.getPlaylistManager().clearDraft(player.getUniqueId());
        MessageUtil.sendFormatted(player, config.getString("playlist-gui.saved-message",
                "%prefix% &#94FF00Your playlist has been saved!"));
        openPlaylistMenu(player, true);
    }

    private void playSavedPlaylist(Player player) {
        List<String> playlist = plugin.getPlaylistManager().getSavedPlaylist(player.getUniqueId());
        if (playlist.isEmpty()) {
            MessageUtil.sendFormatted(player, config.getString("playlist-gui.no-saved-message",
                    "%prefix% &#FF2727You don't have a saved playlist. Select songs and confirm first."));
            return;
        }

        player.closeInventory();
        startPlaylist(player, new ArrayList<>(playlist));
    }

    private void startPlaylist(Player player, List<String> playlist) {
        String first = playlist.get(0);
        List<String> remaining = playlist.size() > 1
                ? new ArrayList<>(playlist.subList(1, playlist.size()))
                : new ArrayList<>();
        plugin.getPlaylistManager().savePlaylist(player.getUniqueId(), playlist);
        plugin.getPlaylistManager().setQueue(player.getUniqueId(), remaining);
        plugin.getPlaylistManager().beginPlaylistPlayback(player.getUniqueId());

        Bukkit.getScheduler().runTask(plugin, () -> playQueuedSong(player, first));
    }

    private void playQueuedSong(Player player, String songId) {
        runCommand(player, config.getString("commands.play", "music play %song%").replace("%song%", songId));
        updateUpNextActionBar(player);
        scheduleAdvanceAfterSong(player, songId);
    }

    private void executeAction(Player player, String action) {
        if (action.startsWith("play:")) {
            String song = action.substring(5);
            runCommand(player, config.getString("commands.play", "music play %song%").replace("%song%", song));
            updateUpNextActionBar(player);
            return;
        }

        if ("skip".equalsIgnoreCase(action)) {
            UUID uuid = player.getUniqueId();
            cancelAdvanceTask(uuid);
            plugin.getPlaylistManager().markManualSkip(uuid);
            runCommand(player, config.getString("commands.skip", "music skip"));
            playNextFromQueue(player);
            return;
        }

        String commandPath = "commands." + action.toLowerCase();
        if (config.contains(commandPath)) {
            runCommand(player, config.getString(commandPath));
            if ("random".equalsIgnoreCase(action)) {
                updateUpNextActionBar(player);
            }
        }
    }

    private void playNextFromQueue(Player player) {
        UUID uuid = player.getUniqueId();
        cancelAdvanceTask(uuid);
        String next = plugin.getPlaylistManager().pollNext(uuid);
        if (next == null) {
            plugin.getPlaylistManager().endPlaylistPlayback(uuid);
            updateUpNextActionBar(player);
            return;
        }
        long delay = config.getLong("playlist-queue.play-delay-ticks", 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            playQueuedSong(player, next);
        }, delay);
    }

    public void updateUpNextActionBar(Player player) {
        if (!config.getBoolean("action-bar.enabled", true)) {
            return;
        }
        String next = plugin.getPlaylistManager().peekNext(player.getUniqueId());
        String display = next == null ? config.getString("action-bar.empty", "None") :
                songDisplayNames.getOrDefault(next, next);
        String template = config.getString("action-bar.format", "&#FF0072&lMUSIC &8▷ &fUp Next is: &#FF0072%song%");
        MessageUtil.sendActionBar(player, template.replace("%song%", display));
    }

    private void runCommand(Player player, String commandTemplate) {
        if (commandTemplate == null || commandTemplate.isEmpty()) {
            return;
        }
        String command = commandTemplate.replace("%player%", player.getName());
        if (config.getBoolean("run-as-console", false)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            player.performCommand(command);
        }
    }
}

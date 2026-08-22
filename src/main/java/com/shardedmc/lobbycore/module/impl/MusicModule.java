package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.gui.MenuHolder;
import com.shardedmc.lobbycore.gui.MenuType;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class MusicModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private final Map<Integer, String> mainSlotActions = new HashMap<>();
    private final Map<Integer, String> playlistSlotSongs = new HashMap<>();
    private final Map<String, String> songDisplayNames = new HashMap<>();
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
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        mainSlotActions.clear();
        playlistSlotSongs.clear();
        songDisplayNames.clear();
        songSections.clear();
        HandlerList.unregisterAll(this);
    }

    private void reloadSongCache() {
        songDisplayNames.clear();
        songSections.clear();
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
        int rows = Math.min(6, Math.max(1, config.getInt("playlist-gui.rows", 6)));
        MenuHolder holder = new MenuHolder(MenuType.MUSIC_PLAYLIST);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                MessageUtil.component(config.getString("playlist-gui.title", "&#FF0072Playlist")));
        holder.setInventory(inventory);
        playlistSlotSongs.clear();

        UUID uuid = player.getUniqueId();
        int slot = 0;
        for (Map.Entry<String, ConfigurationSection> entry : songSections.entrySet()) {
            ConfigurationSection song = entry.getValue();
            String songId = song.getString("song", entry.getKey());
            if (slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, buildPlaylistItem(player, song, songId));
            playlistSlotSongs.put(slot, songId);
            slot++;
        }

        fillEmpty(inventory, player);
        player.openInventory(inventory);
    }

    private ItemStack buildPlaylistItem(Player player, ConfigurationSection song, String songId) {
        UUID uuid = player.getUniqueId();
        boolean selected = plugin.getPlaylistManager().isSelected(uuid, songId);
        if (selected) {
            String displayName = MessageUtil.plainText(song.getString("name", songId));
            List<String> lore = config.getStringList("playlist-gui.selected-lore");
            lore = new ArrayList<>(lore);
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, lore.get(i).replace("%song%", displayName).replace("%song-name%", displayName));
            }
            Material material = Material.matchMaterial(config.getString("playlist-gui.selected-material", "LIME_STAINED_GLASS_PANE"));
            if (material == null) {
                material = Material.LIME_STAINED_GLASS_PANE;
            }
            return ItemBuilder.of(material)
                    .name(MessageUtil.format(config.getString("playlist-gui.selected-name", "&#94FF00&lSELECTED"), player))
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
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (holder.getType() == MenuType.MUSIC_MAIN) {
            handleMainClick(player, event.getRawSlot());
        } else if (holder.getType() == MenuType.MUSIC_PLAYLIST) {
            handlePlaylistClick(player, event.getRawSlot(), holder);
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

    private void handlePlaylistClick(Player player, int slot, MenuHolder holder) {
        String songId = playlistSlotSongs.get(slot);
        if (songId == null) {
            return;
        }

        plugin.getPlaylistManager().toggleSong(player.getUniqueId(), songId);
        updateUpNextActionBar(player);
        openPlaylistMenu(player);
    }

    private void executeAction(Player player, String action) {
        if (action.startsWith("play:")) {
            String song = action.substring(5);
            runCommand(player, config.getString("commands.play", "music play %song%").replace("%song%", song));
            updateUpNextActionBar(player);
            return;
        }

        if ("skip".equalsIgnoreCase(action)) {
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
        String next = plugin.getPlaylistManager().pollNext(player.getUniqueId());
        if (next == null) {
            updateUpNextActionBar(player);
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            runCommand(player, config.getString("commands.play", "music play %song%").replace("%song%", next));
            updateUpNextActionBar(player);
        }, config.getLong("playlist-queue.play-delay-ticks", 5L));
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

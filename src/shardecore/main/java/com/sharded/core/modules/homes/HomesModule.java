package com.sharded.core.modules.homes;

import com.sharded.core.ShardedCore;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.SetupFeatureModule;
import com.sharded.core.modules.combat.CombatModule;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.MaterialUtil;
import com.sharded.core.setup.util.MessageUtil;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.setup.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class HomesModule extends SetupFeatureModule implements CommandExecutor, TabCompleter, Listener {

    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<UUID, TeleportSession> sessions = new HashMap<>();
    private final Map<UUID, String> pendingDelete = new HashMap<>();
    private File dataFile;
    private FileConfiguration data;

    public HomesModule(ShardedCore plugin) {
        super(plugin, "homes");
    }

    @Override
    protected void onEnable() {
        loadFiles();
        dataFile = new File(folder, "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create homes data.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadHomes();
        for (String cmd : List.of("homes", "home", "sethome", "delhome")) {
            if (plugin.getCommand(cmd) != null) {
                plugin.getCommand(cmd).setExecutor(this);
                plugin.getCommand(cmd).setTabCompleter(this);
            }
        }
    }

    @Override
    protected void onDisable() {
        sessions.values().forEach(TeleportSession::cancel);
        sessions.clear();
        saveHomes();
        // listeners unregistered by Module.disable()
    }

    private void loadHomes() {
        homes.clear();
        if (data.getConfigurationSection("players") == null) {
            return;
        }
        for (String uuidKey : data.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidKey);
            Map<String, Location> map = new HashMap<>();
            if (data.getConfigurationSection("players." + uuidKey) != null) {
                for (String name : data.getConfigurationSection("players." + uuidKey).getKeys(false)) {
                    String path = "players." + uuidKey + "." + name + ".";
                    World world = Bukkit.getWorld(data.getString(path + "world", "world"));
                    if (world == null) {
                        continue;
                    }
                    map.put(name.toLowerCase(Locale.ROOT), new Location(world,
                            data.getDouble(path + "x"),
                            data.getDouble(path + "y"),
                            data.getDouble(path + "z"),
                            (float) data.getDouble(path + "yaw"),
                            (float) data.getDouble(path + "pitch")));
                }
            }
            homes.put(uuid, map);
        }
    }

    private void saveHomes() {
        data.set("players", null);
        for (Map.Entry<UUID, Map<String, Location>> entry : homes.entrySet()) {
            for (Map.Entry<String, Location> home : entry.getValue().entrySet()) {
                Location loc = home.getValue();
                if (loc.getWorld() == null) {
                    continue;
                }
                String path = "players." + entry.getKey() + "." + home.getKey() + ".";
                data.set(path + "world", loc.getWorld().getName());
                data.set(path + "x", loc.getX());
                data.set(path + "y", loc.getY());
                data.set(path + "z", loc.getZ());
                data.set(path + "yaw", loc.getYaw());
                data.set(path + "pitch", loc.getPitch());
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving homes", e);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "homes" -> {
                openGui(player);
                yield true;
            }
            case "sethome" -> {
                String name = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "home";
                setHome(player, name);
                yield true;
            }
            case "delhome" -> {
                if (args.length < 1) {
                    feedback(player, plugin.errorColor() + msg("usage-del", "Usage: /delhome <name>"));
                } else {
                    deleteHome(player, args[0].toLowerCase(Locale.ROOT));
                }
                yield true;
            }
            default -> {
                String name = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "home";
                teleportHome(player, name);
                yield true;
            }
        };
    }

    private int maxHomes(Player player) {
        int cap = config.getIntegerList("bed-slots").isEmpty() ? 14 : config.getIntegerList("bed-slots").size();
        if (this.hasSetupPerm(player, "setupcore.homes.unlimited") || player.hasPermission("sharded.homes.unlimited")) {
            return Math.max(1, cap);
        }
        int max = Math.max(1, config.getInt("default-homes", 1));
        org.bukkit.configuration.ConfigurationSection limits = config.getConfigurationSection("home-limits");
        if (limits != null) {
            for (String permission : limits.getKeys(false)) {
                int granted = limits.getInt(permission, 0);
                if (granted > max && player.hasPermission(permission)) {
                    max = granted;
                }
            }
        }
        return Math.min(max, cap);
    }

    private void setHome(Player player, String name) {
        int max = maxHomes(player);
        Map<String, Location> map = homes.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        if (!map.containsKey(name) && map.size() >= max && !this.hasSetupPerm(player, "setupcore.homes.unlimited")) {
            feedback(player, plugin.errorColor() + msg("max-homes", "You have reached the home limit."));
            return;
        }
        map.put(name, player.getLocation().clone());
        saveHomes();
        feedback(player, plugin.successColor() + msg("set", "Home %home% set.").replace("%home%", name));
        SoundUtil.play(player, config.getString("sound-success", "entity.experience_orb.pickup"));
    }

    private void deleteHome(Player player, String name) {
        Map<String, Location> map = homes.get(player.getUniqueId());
        if (map == null || map.remove(name) == null) {
            feedback(player, plugin.errorColor() + msg("not-found", "Home not found."));
            return;
        }
        saveHomes();
        feedback(player, plugin.successColor() + msg("deleted", "Home %home% deleted.").replace("%home%", name));
    }

    private void teleportHome(Player player, String name) {
        CombatModule combat = plugin.modules().get(CombatModule.class);
        if (combat != null && combat.isTagged(player)) {
            feedback(player, plugin.errorColor() + msg("in-combat", "Cannot teleport while in combat."));
            return;
        }
        Map<String, Location> map = homes.get(player.getUniqueId());
        if (map == null || !map.containsKey(name)) {
            feedback(player, plugin.errorColor() + msg("not-found", "Home not found."));
            return;
        }
        cancelSession(player.getUniqueId());
        TeleportSession session = new TeleportSession(player, map.get(name).clone(),
                config.getInt("teleport-seconds", 5));
        sessions.put(player.getUniqueId(), session);
        session.start();
    }

    private void openGui(Player player) {
        int rows = config.getInt("rows", 6);
        Inventory inventory = GuiHelper.create("homes", config.getString("gui-title", "&8Homes"), rows);
        GuiHelper.fillGlass(inventory);

        List<Integer> bedSlots = config.getIntegerList("bed-slots");
        if (bedSlots.isEmpty()) {
            bedSlots = List.of(10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34);
        }
        List<Integer> setSlots = config.getIntegerList("set-slots");
        if (setSlots.isEmpty()) {
            setSlots = List.of(19, 20, 21, 22, 23, 24, 25, 37, 38, 39, 40, 41, 42, 43);
        }

        Map<String, Location> map = homes.getOrDefault(player.getUniqueId(), Map.of());
        Material setBed = MaterialUtil.resolve(config.getString("set-bed-material"), Material.BLUE_BED);
        Material emptyBed = MaterialUtil.resolve(config.getString("empty-bed-material"), Material.GRAY_BED);
        Material setDye = MaterialUtil.resolveWithFallbacks(
                config.getString("set-bundle-material"), "LIGHT_BLUE_DYE", "BLUE_DYE");
        Material emptyDye = MaterialUtil.resolveWithFallbacks(
                config.getString("empty-bundle-material"), "GRAY_DYE", "GRAY_BUNDLE");
        if (setDye == null) {
            setDye = Material.LIGHT_BLUE_DYE;
        }
        if (emptyDye == null) {
            emptyDye = Material.GRAY_DYE;
        }

        for (int i = 0; i < Math.min(bedSlots.size(), maxHomes(player)); i++) {
            int index = i + 1;
            String homeKey = String.valueOf(index);
            boolean exists = map.containsKey(homeKey) || map.containsKey("home" + index);
            Location loc = map.get(homeKey);
            if (loc == null) {
                loc = map.get("home" + index);
            }
            if (i == 0 && loc == null) {
                loc = map.get("home");
                exists = loc != null;
            }

            if (exists && loc != null) {
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList("home-set-lore")) {
                    lore.add(line.replace("%index%", String.valueOf(index))
                            .replace("%home%", homeKey)
                            .replace("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "?")
                            .replace("%x%", String.valueOf(loc.getBlockX()))
                            .replace("%y%", String.valueOf(loc.getBlockY()))
                            .replace("%z%", String.valueOf(loc.getBlockZ())));
                }
                inventory.setItem(bedSlots.get(i), ItemBuilder.of(setBed)
                        .name(config.getString("home-set-name", "&#0083FF&lHOME &#0083FF%index%")
                                .replace("%index%", String.valueOf(index)))
                        .lore(lore)
                        .hideExtras()
                        .build());
                if (i < setSlots.size()) {
                    inventory.setItem(setSlots.get(i), ItemBuilder.of(setDye)
                            .name(config.getString("bundle-set-name", "&#0083FF&lDELETE"))
                            .hideExtras()
                            .build());
                }
            } else {
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList("home-empty-lore")) {
                    lore.add(line.replace("%index%", String.valueOf(index)));
                }
                inventory.setItem(bedSlots.get(i), ItemBuilder.of(emptyBed)
                        .name(config.getString("home-empty-name", "&7&lNO HOME &7Set")
                                .replace("%index%", String.valueOf(index)))
                        .lore(lore)
                        .hideExtras()
                        .build());
                if (i < setSlots.size()) {
                    inventory.setItem(setSlots.get(i), ItemBuilder.of(emptyDye)
                            .name(config.getString("bundle-empty-name", "&7&lSET HOME"))
                            .hideExtras()
                            .build());
                }
            }
        }
        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, String homeKey, int index) {
        pendingDelete.put(player.getUniqueId(), homeKey);
        int rows = config.getInt("delete-confirm.rows", 3);
        Inventory inventory = GuiHelper.create("homes-delete", "&8Delete Home", rows);
        GuiHelper.fillGlass(inventory);
        int confirmSlot = config.getInt("delete-confirm.confirm-slot", 10);
        int bedSlot = config.getInt("delete-confirm.bed-slot", 13);
        int cancelSlot = config.getInt("delete-confirm.cancel-slot", 15);
        Material setBed = MaterialUtil.resolve(config.getString("set-bed-material"), Material.BLUE_BED);
        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList("home-delete-lore")) {
            lore.add(line.replace("%index%", String.valueOf(index)).replace("%home%", homeKey));
        }
        inventory.setItem(confirmSlot, GuiHelper.confirmButton("Confirm delete"));
        inventory.setItem(bedSlot, ItemBuilder.of(setBed)
                .name(config.getString("home-set-name", "&#0083FF&lHOME &#0083FF%index%")
                        .replace("%index%", String.valueOf(index)))
                .lore(lore)
                .hideExtras()
                .build());
        inventory.setItem(cancelSlot, GuiHelper.cancelButton());
        player.openInventory(inventory);
    }

    private String resolveHomeKey(Map<String, Location> map, int index) {
        String homeKey = String.valueOf(index);
        if (map.containsKey(homeKey)) {
            return homeKey;
        }
        if (map.containsKey("home" + index)) {
            return "home" + index;
        }
        if (index == 1 && map.containsKey("home")) {
            return "home";
        }
        return homeKey;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        if ("homes-delete".equals(holder.getId())) {
            event.setCancelled(true);
            int confirmSlot = config.getInt("delete-confirm.confirm-slot", 10);
            int cancelSlot = config.getInt("delete-confirm.cancel-slot", 15);
            if (event.getRawSlot() == confirmSlot) {
                String key = pendingDelete.remove(player.getUniqueId());
                if (key != null) {
                    deleteHome(player, key);
                }
                openGui(player);
            } else if (event.getRawSlot() == cancelSlot) {
                pendingDelete.remove(player.getUniqueId());
                openGui(player);
            }
            return;
        }
        if (!"homes".equals(holder.getId())) {
            return;
        }
        event.setCancelled(true);
        List<Integer> bedSlots = config.getIntegerList("bed-slots");
        if (bedSlots.isEmpty()) {
            bedSlots = List.of(10, 11, 12, 13, 14, 15, 16, 28, 29, 30, 31, 32, 33, 34);
        }
        List<Integer> setSlots = config.getIntegerList("set-slots");
        if (setSlots.isEmpty()) {
            setSlots = List.of(19, 20, 21, 22, 23, 24, 25, 37, 38, 39, 40, 41, 42, 43);
        }
        int raw = event.getRawSlot();
        int bedIndex = bedSlots.indexOf(raw);
        int dyeIndex = setSlots.indexOf(raw);
        Map<String, Location> map = homes.getOrDefault(player.getUniqueId(), Map.of());

        if (dyeIndex >= 0) {
            int index = dyeIndex + 1;
            if (index > maxHomes(player)) {
                return;
            }
            String key = resolveHomeKey(map, index);
            boolean exists = map.containsKey(key);
            if (exists) {
                openDeleteConfirm(player, key, index);
            } else {
                player.closeInventory();
                setHome(player, String.valueOf(index));
            }
            return;
        }

        if (bedIndex < 0) {
            return;
        }
        int index = bedIndex + 1;
        if (index > maxHomes(player)) {
            return;
        }
        String homeKey = String.valueOf(index);
        boolean exists = map.containsKey(homeKey) || map.containsKey("home" + index)
                || (index == 1 && map.containsKey("home"));
        if (exists) {
            String key = resolveHomeKey(map, index);
            player.closeInventory();
            teleportHome(player, key);
        } else {
            player.closeInventory();
            setHome(player, homeKey);
        }
    }

    private void feedback(Player player, String message) {
        MessageUtil.send(player, config, prefix() + message);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (sessions.isEmpty() || !event.hasChangedBlock()) {
            return;
        }
        TeleportSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            cancelSession(event.getPlayer().getUniqueId());
            TextUtil.sendActionBar(event.getPlayer(), prefix() + plugin.errorColor()
                    + msg("cancelled-move", "Teleport cancelled."));
            SoundUtil.play(event.getPlayer(), config.getString("sound-cancel", "entity.villager.no"));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelSession(event.getPlayer().getUniqueId());
    }

    private void cancelSession(UUID uuid) {
        TeleportSession session = sessions.remove(uuid);
        if (session != null) {
            session.cancel();
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) {
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("home") || command.getName().equalsIgnoreCase("delhome")) {
            return homes.getOrDefault(player.getUniqueId(), Map.of()).keySet().stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    public String prefix() {
        return messages.getString("prefix", "&#FF0067&lHOMES &8▷ &r");
    }

    private final class TeleportSession {
        private final Player player;
        private final Location destination;
        private int secondsLeft;
        private BukkitTask task;

        private TeleportSession(Player player, Location destination, int seconds) {
            this.player = player;
            this.destination = destination;
            this.secondsLeft = seconds;
        }

        private void start() {
            task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                if (secondsLeft <= 0) {
                    player.teleport(destination);
                    TextUtil.sendActionBar(player, prefix() + plugin.successColor()
                            + msg("teleported", "Teleported home."));
                    SoundUtil.play(player, config.getString("sound-teleport", "entity.enderman.teleport"));
                    sessions.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                TextUtil.sendActionBar(player, prefix() + msg("countdown", "&fTeleporting home in &#FF0067&n%seconds%s")
                        .replace("%seconds%", String.valueOf(secondsLeft)));
                SoundUtil.play(player, config.getString("sound-countdown", "block.note_block.hat"));
                secondsLeft--;
            }, 0L, 20L);
        }

        private void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }
}

package com.shardedcore.modules.homes;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class HomesModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private final Map<UUID, Map<Integer, Home>> cache = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();
    private final Map<Integer, Slot> slots = new ConcurrentHashMap<>();
    private Sqlite sqlite;

    public HomesModule(ShardedCore plugin) {
        super(plugin, "homes");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS homes (
                        uuid TEXT NOT NULL,
                        number INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL,
                        PRIMARY KEY (uuid, number)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create homes table", ex);
        }
        indexSlots();
        registerCommand("homes", this);
        registerCommand("home", this);
        registerCommand("sethome", this);
        registerCommand("delhome", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        cache.clear();
        slots.clear();
        cleanup();
    }

    @Override
    public void reload() {
        super.reload();
        indexSlots();
    }

    @Override
    protected void send(CommandSender to, String path, String... pairs) {
        String message = Text.apply(cfg("messages." + path, cfg(path, "")), pairs);
        if (message == null || message.isEmpty()) return;
        boolean actionbar = config.getBoolean("actionbar", false) || config.getBoolean("messages.actionbar", false);
        if (to instanceof Player player && actionbar && !path.startsWith("usage")) {
            player.sendActionBar(ColorUtil.parse(message));
            return;
        }
        to.sendMessage(ColorUtil.parse(message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ColorUtil.parse("&#FF0000&lERROR &8▷ &fOnly a player can use that."));
            return true;
        }
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "homes" -> {
                openGui(player);
                yield true;
            }
            case "home" -> {
                Integer number = args.length == 0 ? 1 : parseNumber(args[0]);
                if (number == null) send(player, "home-not-found");
                else teleport(player, number);
                yield true;
            }
            case "sethome" -> {
                Integer number = args.length == 0 ? 1 : parseNumber(args[0]);
                if (number == null) send(player, "home-not-found");
                else setHome(player, number);
                yield true;
            }
            case "delhome" -> {
                if (args.length == 0) {
                    send(player, "usage-delhome");
                    yield true;
                }
                Integer number = parseNumber(args[0]);
                if (number == null) send(player, "home-not-found");
                else deleteHome(player, number, config.getBoolean("confirm-delete", true));
                yield true;
            }
            default -> true;
        };
    }

    private void openGui(Player player) {
        homes(player.getUniqueId());
        int size = config.getInt("gui.size", 54);
        int rows = Math.max(1, Math.min(6, size / 9));
        Menus.Menu menu = plugin.menus().create(player, cfg("gui.title", "&8Homes | Dashboard"), rows);
        int allowed = allowed(player);
        for (Slot slot : slots.values()) {
            boolean unlocked = slot.number <= allowed;
            boolean set = unlocked && has(player.getUniqueId(), slot.number);
            ItemStack bed = item(player, slot, false, unlocked, set);
            ItemStack dye = item(player, slot, true, unlocked, set);
            menu.set(slot.bed, bed, event -> {
                event.setCancelled(true);
                sound(player, "gui.click-sound");
                click(player, slot.number, false, unlocked, set);
            });
            menu.set(slot.dye, dye, event -> {
                event.setCancelled(true);
                sound(player, "gui.click-sound");
                click(player, slot.number, true, unlocked, set);
            });
        }
        plugin.menus().open(player, menu);
        sound(player, "gui.open-sound");
    }

    private void click(Player player, int number, boolean dye, boolean unlocked, boolean set) {
        if (!unlocked) return;
        if (!set) {
            setHome(player, number);
            if (player.isOnline()) openGui(player);
            return;
        }
        if (dye) {
            deleteHome(player, number, config.getBoolean("confirm-delete", true));
            return;
        }
        player.closeInventory();
        teleport(player, number);
    }

    private ItemStack item(Player player, Slot slot, boolean dye, boolean unlocked, boolean set) {
        String kind = dye ? "dye" : "bed";
        ConfigurationSection section;
        if (!unlocked) {
            section = config.getConfigurationSection("gui.locked." + kind);
        } else {
            String state = set ? "set" : "not-set";
            section = config.getConfigurationSection("gui.homes.home-" + slot.number + "." + kind + "." + state);
        }
        return Items.fromSection(section, player, "number", String.valueOf(slot.number));
    }

    private void openConfirm(Player player, int number) {
        int size = config.getInt("confirm.size", 27);
        int rows = Math.max(1, Math.min(6, size / 9));
        Menus.Menu menu = plugin.menus().create(player, cfg("confirm.title", "&8Delete Home"), rows);
        ConfigurationSection cancel = config.getConfigurationSection("confirm.cancel");
        ConfigurationSection info = config.getConfigurationSection("confirm.info");
        ConfigurationSection confirm = config.getConfigurationSection("confirm.confirm");
        if (cancel != null) {
            menu.set(cancel.getInt("slot", 11), Items.fromSection(cancel, player, "number", String.valueOf(number)), event -> {
                event.setCancelled(true);
                sound(player, "gui.click-sound");
                openGui(player);
            });
        }
        if (info != null) {
            menu.set(info.getInt("slot", 13), Items.fromSection(info, player, "number", String.valueOf(number)));
        }
        if (confirm != null) {
            menu.set(confirm.getInt("slot", 15), Items.fromSection(confirm, player, "number", String.valueOf(number)), event -> {
                event.setCancelled(true);
                sound(player, "gui.click-sound");
                deleteHome(player, number, false);
                openGui(player);
            });
        }
        plugin.menus().open(player, menu);
    }

    private void setHome(Player player, int number) {
        if (number < 1 || number > config.getInt("homes", 14)) {
            send(player, "home-not-found");
            return;
        }
        if (number > allowed(player)) {
            send(player, "home-limit", "max", String.valueOf(allowed(player)));
            return;
        }
        String world = player.getWorld().getName();
        for (String disabled : config.getStringList("disabled-worlds")) {
            if (disabled.equalsIgnoreCase(world)) {
                send(player, "home-world-blacklist");
                return;
            }
        }
        Location loc = player.getLocation();
        Home home = new Home(player.getUniqueId(), number, world, loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
        homes(player.getUniqueId()).put(number, home);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO homes (uuid, number, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(uuid, number) DO UPDATE SET
                            world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z,
                            yaw = excluded.yaw, pitch = excluded.pitch
                        """, home.uuid.toString(), home.number, home.world, home.x, home.y, home.z, home.yaw, home.pitch);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save home", ex);
            }
        });
        send(player, "home-set", "number", String.valueOf(number));
    }

    private void deleteHome(Player player, int number, boolean confirm) {
        if (number < 1 || number > config.getInt("homes", 14)) {
            send(player, "home-not-found");
            return;
        }
        if (!has(player.getUniqueId(), number)) {
            send(player, "home-not-found");
            return;
        }
        if (confirm) {
            openConfirm(player, number);
            return;
        }
        homes(player.getUniqueId()).remove(number);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM homes WHERE uuid = ? AND number = ?", player.getUniqueId().toString(), number);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to delete home", ex);
            }
        });
        send(player, "home-delete", "number", String.valueOf(number));
    }

    private void teleport(Player player, int number) {
        if (number < 1 || number > config.getInt("homes", 14)) {
            send(player, "home-not-found");
            return;
        }
        if (number > allowed(player)) {
            send(player, "home-limit", "max", String.valueOf(allowed(player)));
            return;
        }
        Home home = homes(player.getUniqueId()).get(number);
        if (home == null) {
            send(player, "home-not-found");
            return;
        }
        Location dest = home.location();
        if (dest == null) {
            send(player, "home-not-found");
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            send(player, "already-teleporting");
            return;
        }
        int seconds = Math.max(0, config.getInt("delay-seconds", 5));
        if (seconds == 0) {
            finishTeleport(player, dest);
            return;
        }
        int[] left = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stop(player.getUniqueId());
                return;
            }
            if (left[0] <= 0) {
                stop(player.getUniqueId());
                finishTeleport(player, dest);
                return;
            }
            send(player, "teleport", "number", String.valueOf(left[0]));
            sound(player, "sounds.countdown");
            left[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), task);
    }

    private void finishTeleport(Player player, Location dest) {
        player.teleportAsync(dest).thenAccept(ok -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!Boolean.TRUE.equals(ok) || !player.isOnline()) return;
            sound(player, "sounds.complete");
            send(player, "teleport-success");
        }));
    }

    private void stop(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!config.getBoolean("cancel-on-move", true)) return;
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!pending.containsKey(event.getPlayer().getUniqueId())) return;
        stop(event.getPlayer().getUniqueId());
        sound(event.getPlayer(), "sounds.cancel");
        send(event.getPlayer(), "teleport-cancel");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer().getUniqueId());
    }

    int allowed(Player player) {
        int cap = Math.max(1, config.getInt("homes", 14));
        int highest = 0;
        for (int i = 1; i <= cap; i++) {
            if (player.hasPermission("shardedcore.homes.limit." + i)
                    || player.hasPermission("shardedcore.homes." + i)
                    || player.hasPermission("homes." + i)
                    || player.hasPermission("homes.limit." + i)) {
                highest = i;
            }
        }
        if (highest == 0) highest = Math.max(0, config.getInt("default-homes", 3));
        return Math.min(highest, cap);
    }

    private Map<Integer, Home> homes(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::load);
    }

    private boolean has(UUID uuid, int number) {
        return homes(uuid).containsKey(number);
    }

    private Map<Integer, Home> load(UUID uuid) {
        Map<Integer, Home> homes = new ConcurrentHashMap<>();
        try {
            sqlite.query("SELECT number, world, x, y, z, yaw, pitch FROM homes WHERE uuid = ?", rs -> {
                try {
                    while (rs.next()) {
                        int number = rs.getInt("number");
                        homes.put(number, new Home(uuid, number, rs.getString("world"),
                                rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"),
                                rs.getFloat("yaw"), rs.getFloat("pitch")));
                    }
                } catch (SQLException ex) {
                    throw new IllegalStateException(ex);
                }
                return homes;
            }, uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load homes for " + uuid, ex);
        }
        return homes;
    }

    private void indexSlots() {
        slots.clear();
        ConfigurationSection section = config.getConfigurationSection("gui.homes");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (!key.startsWith("home-")) continue;
            try {
                int number = Integer.parseInt(key.substring(5));
                ConfigurationSection home = section.getConfigurationSection(key);
                if (home == null) continue;
                int bed = home.getInt("bed.set.slot", home.getInt("bed.not-set.slot", -1));
                int dye = home.getInt("dye.set.slot", home.getInt("dye.not-set.slot", -1));
                slots.put(number, new Slot(number, bed, dye));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private Integer parseNumber(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("home") && !name.equals("sethome") && !name.equals("delhome")) return List.of();
        List<String> numbers = new ArrayList<>();
        int max = allowed(player);
        for (int i = 1; i <= max; i++) numbers.add(String.valueOf(i));
        return Tabs.filter(numbers, args[0]);
    }

    private record Slot(int number, int bed, int dye) {
    }

    private record Home(UUID uuid, int number, String world, double x, double y, double z, float yaw, float pitch) {
        Location location() {
            World loaded = Bukkit.getWorld(world);
            if (loaded == null) return null;
            return new Location(loaded, x, y, z, yaw, pitch);
        }
    }
}

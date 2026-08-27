package com.shardedcore.modules.chatcolor;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.CosmeticsMenus;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Perms;
import com.shardedcore.util.Tabs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class ChatColorModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> unlocked = new ConcurrentHashMap<>();

    public ChatColorModule(ShardedCore plugin) {
        super(plugin, "chatcolor");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS chatcolor_selected (
                        uuid TEXT PRIMARY KEY,
                        color TEXT NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS chatcolor_unlocks (
                        uuid TEXT NOT NULL,
                        color TEXT NOT NULL,
                        PRIMARY KEY (uuid, color)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create chatcolor tables", ex);
        }
        registerCommand("chatcolor", this);
        registerCommand("chatcolorgradient", this);
        registerListener(this);
        registerAll();
    }

    @Override
    public void disable() {
        selected.clear();
        unlocked.clear();
        cleanup();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message.toLowerCase(Locale.ROOT).startsWith("/chatcolor:gradient")) {
            event.setMessage("/chatcolorgradient" + message.substring("/chatcolor:gradient".length()));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        selected.remove(uuid);
        unlocked.remove(uuid);
    }

    public String color(Player player, String text) {
        ColorDef def = definition(selected(player.getUniqueId()));
        if (def == null || text == null || text.isEmpty()) return text == null ? "" : text;
        if ("default".equals(def.id) || "clear".equals(def.id)) return text;
        if ("gradient".equals(def.type)) return ColorUtil.gradient(text, def.hex, def.hex2);
        return ColorUtil.solid(text, def.hex);
    }

    public String display(Player player) {
        ColorDef def = definition(selected(player.getUniqueId()));
        if (def == null) return "";
        if ("gradient".equals(def.type)) return ColorUtil.gradient(def.id, def.hex, def.hex2);
        return ColorUtil.solid(def.id, def.hex);
    }

    public boolean exists(String name) {
        return definition(name) != null;
    }

    public boolean unlock(UUID uuid, String name) {
        ColorDef def = definition(name);
        if (def == null) return false;
        unlocked(uuid).add(def.id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("INSERT OR IGNORE INTO chatcolor_unlocks (uuid, color) VALUES (?, ?)", uuid.toString(), def.id);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to unlock chatcolor", ex);
            }
        });
        return true;
    }

    public boolean apply(UUID uuid, String name) {
        ColorDef def = definition(name);
        if (def == null) return false;
        selected.put(uuid, def.id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO chatcolor_selected (uuid, color) VALUES (?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET color = excluded.color
                        """, uuid.toString(), def.id);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to save chatcolor", ex);
            }
        });
        return true;
    }

    public List<String> names() {
        ConfigurationSection section = config.getConfigurationSection("colors");
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean gradientCommand = command.getName().equalsIgnoreCase("chatcolorgradient")
                || label.equalsIgnoreCase("chatcolor:gradient");
        if (gradientCommand) {
            return gradient(sender, args.length > 0 && args[0].equalsIgnoreCase("create")
                    ? args
                    : prepend(args, "create"));
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            openGui(player, 0);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args);
            case "set" -> set(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "gradient" -> gradient(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            case "clear", "off" -> clear(sender);
            default -> {
                send(sender, "usage");
                yield true;
            }
        };
    }

    private boolean create(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 3) {
            send(sender, "usage-create");
            return true;
        }
        String name = sanitize(args[1]);
        String hex = ColorUtil.hex(args[2]);
        if (name.isEmpty() || hex.isEmpty()) {
            send(sender, "invalid");
            return true;
        }
        config.set("colors." + name + ".type", "solid");
        config.set("colors." + name + ".hex", hex);
        Configs.save(config, new File(folder, "config.yml"));
        Perms.ensure("shardedcore.chatcolor." + name);
        send(sender, "created", "color", name, "hex", "#" + hex);
        return true;
    }

    private boolean gradient(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length >= 1 && args[0].equalsIgnoreCase("create")) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        String name;
        String hex1;
        String hex2;
        if (args.length >= 3) {
            name = sanitize(args[0]);
            hex1 = ColorUtil.hex(args[1]);
            hex2 = ColorUtil.hex(args[2]);
        } else if (args.length == 2) {
            hex1 = ColorUtil.hex(args[0]);
            hex2 = ColorUtil.hex(args[1]);
            name = sanitize((hex1 + "-" + hex2).toLowerCase(Locale.ROOT));
        } else {
            send(sender, "usage-gradient");
            return true;
        }
        if (name.isEmpty() || hex1.isEmpty() || hex2.isEmpty()) {
            send(sender, "invalid");
            return true;
        }
        config.set("colors." + name + ".type", "gradient");
        config.set("colors." + name + ".hex", hex1);
        config.set("colors." + name + ".hex2", hex2);
        Configs.save(config, new File(folder, "config.yml"));
        Perms.ensure("shardedcore.chatcolor." + name);
        send(sender, "created-gradient", "color", name, "from", "#" + hex1, "to", "#" + hex2);
        return true;
    }

    private boolean set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length < 2) {
            send(sender, "usage-set");
            return true;
        }
        ColorDef def = definition(args[1]);
        if (def == null) {
            send(player, "missing", "color", args[1]);
            return true;
        }
        if (!canUse(player, def.id)) {
            send(player, "locked", "color", def.id);
            return true;
        }
        apply(player.getUniqueId(), def.id);
        send(player, "set", "color", displayName(def));
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (!admin(sender)) return true;
        if (args.length < 2) {
            send(sender, "usage-remove");
            return true;
        }
        String name = sanitize(args[1]);
        if (definition(name) == null) {
            send(sender, "missing", "color", args[1]);
            return true;
        }
        config.set("colors." + name, null);
        Configs.save(config, new File(folder, "config.yml"));
        send(sender, "removed", "color", name);
        return true;
    }

    private boolean clear(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        selected.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM chatcolor_selected WHERE uuid = ?", player.getUniqueId().toString());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to clear chatcolor", ex);
            }
        });
        send(player, "cleared");
        return true;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("shardedcore.chatcolor.admin")) return true;
        send(sender, "no-permission");
        return false;
    }

    public boolean canUse(Player player, String name) {
        if (name != null && name.equalsIgnoreCase("default")) return true;
        if (player.hasPermission("shardedcore.chatcolor.admin")) return true;
        if (player.hasPermission("shardedcore.chatcolor." + name)) return true;
        return unlocked(player.getUniqueId()).contains(name.toLowerCase(Locale.ROOT));
    }

    public void wipe(UUID uuid) {
        selected.remove(uuid);
        unlocked.remove(uuid);
        try {
            sqlite.execute("DELETE FROM chatcolor_selected WHERE uuid = ?", uuid.toString());
            sqlite.execute("DELETE FROM chatcolor_unlocks WHERE uuid = ?", uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to wipe chatcolor", ex);
        }
    }

    private void openGui(Player player, int page) {
        List<CosmeticsMenus.Entry> entries = new ArrayList<>();
        for (String id : names()) {
            ColorDef def = definition(id);
            if (def == null) continue;
            String color = ColorUtil.colorCode(def.hex == null || def.hex.isBlank() ? "0083FF" : def.hex);
            String title = CosmeticsMenus.pretty(def.id);
            String display = title + " Colour";
            entries.add(new CosmeticsMenus.Entry(def.id, title, display,
                    cfg("gui.default-description", "&8Description"), color, canUse(player, def.id)));
        }
        CosmeticsMenus.open(plugin, player, config, cfg("gui.title", "☀ Chatcolors ☀ Previewing | Colors"),
                entries, page, "color", false,
                next -> openGui(player, next),
                null,
                entry -> {
                    if ("default".equalsIgnoreCase(entry.id()) || "clear".equalsIgnoreCase(entry.id())) {
                        clear(player);
                        openGui(player, page);
                        return;
                    }
                    if (!canUse(player, entry.id())) {
                        send(player, "locked", "color", entry.id());
                        sound(player, "sounds.error");
                        return;
                    }
                    apply(player.getUniqueId(), entry.id());
                    send(player, "set", "color", displayName(definition(entry.id())));
                    sound(player, "sounds.equip");
                    openGui(player, page);
                },
                null,
                () -> {
                    clear(player);
                    openGui(player, page);
                });
    }

    private void registerAll() {
        for (String name : names()) Perms.ensure("shardedcore.chatcolor." + name);
    }

    private ColorDef definition(String name) {
        if (name == null || name.isBlank()) return null;
        String id = sanitize(name);
        ConfigurationSection section = config.getConfigurationSection("colors." + id);
        if (section == null) {
            ConfigurationSection root = config.getConfigurationSection("colors");
            if (root != null) {
                for (String key : root.getKeys(false)) {
                    if (key.equalsIgnoreCase(name)) {
                        id = key;
                        section = root.getConfigurationSection(key);
                        break;
                    }
                }
            }
        }
        if (section == null) return null;
        return new ColorDef(id, section.getString("type", "solid"),
                ColorUtil.hex(section.getString("hex", "")),
                ColorUtil.hex(section.getString("hex2", section.getString("to", ""))));
    }

    private String selected(UUID uuid) {
        return selected.computeIfAbsent(uuid, id -> {
            try {
                return sqlite.query("SELECT color FROM chatcolor_selected WHERE uuid = ?", rs -> {
                    try {
                        return rs.next() ? rs.getString("color") : "";
                    } catch (SQLException ex) {
                        return "";
                    }
                }, id.toString());
            } catch (SQLException ex) {
                return "";
            }
        });
    }

    private java.util.Set<String> unlocked(UUID uuid) {
        return unlocked.computeIfAbsent(uuid, id -> {
            java.util.Set<String> set = ConcurrentHashMap.newKeySet();
            try {
                sqlite.query("SELECT color FROM chatcolor_unlocks WHERE uuid = ?", rs -> {
                    try {
                        while (rs.next()) set.add(rs.getString("color"));
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                    return set;
                }, id.toString());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load chatcolor unlocks", ex);
            }
            return set;
        });
    }

    private String displayName(ColorDef def) {
        if ("gradient".equals(def.type)) return ColorUtil.gradient(def.id, def.hex, def.hex2);
        return ColorUtil.solid(def.id, def.hex);
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    private static String[] prepend(String[] args, String first) {
        String[] next = new String[args.length + 1];
        next[0] = first;
        System.arraycopy(args, 0, next, 1, args.length);
        return next;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("chatcolorgradient")) {
            if (args.length == 1) return Tabs.filter(List.of("create"), args[0]);
            return List.of();
        }
        if (args.length == 1) return Tabs.filter(List.of("create", "set", "remove", "gradient", "clear"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))) {
            return Tabs.filter(names(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("gradient")) return Tabs.filter(List.of("create"), args[1]);
        return List.of();
    }

    private record ColorDef(String id, String type, String hex, String hex2) {
    }
}

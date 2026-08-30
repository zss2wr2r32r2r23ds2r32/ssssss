package com.shardedcore.modules.tags;

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
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class TagsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> unlocked = new ConcurrentHashMap<>();

    public TagsModule(ShardedCore plugin) {
        super(plugin, "tags");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS tag_selected (
                        uuid TEXT PRIMARY KEY,
                        tag TEXT NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS tag_unlocks (
                        uuid TEXT NOT NULL,
                        tag TEXT NOT NULL,
                        PRIMARY KEY (uuid, tag)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create tag tables", ex);
        }
        registerCommand("tag", this);
        registerCommand("tags", this);
        registerListener(this);
        registerAll();
        for (Player player : Bukkit.getOnlinePlayers()) clearTab(player);
    }

    @Override
    public void disable() {
        for (Player player : Bukkit.getOnlinePlayers()) clearTab(player);
        selected.clear();
        unlocked.clear();
        cleanup();
    }

    public String raw(Player player) {
        TagDef def = definition(selected(player.getUniqueId()));
        return def == null ? "" : def.text;
    }

    public String display(Player player) {
        String tag = raw(player);
        if (tag == null || tag.isBlank()) return "";
        return cfg("tab-format", " %tag%").replace("%tag%", tag);
    }

    public String chatPrefix(Player player) {
        String tag = raw(player);
        if (tag == null || tag.isBlank()) return "";
        return cfg("chat-format", " %tag%").replace("%tag%", tag);
    }

    public boolean exists(String name) {
        return definition(name) != null;
    }

    public List<String> names() {
        return keys("tags");
    }

    public List<String> limitedNames() {
        return keys("limited");
    }

    public boolean unlock(UUID uuid, String name) {
        TagDef def = definition(name);
        if (def == null) return false;
        unlocked(uuid).add(def.id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("INSERT OR IGNORE INTO tag_unlocks (uuid, tag) VALUES (?, ?)", uuid.toString(), def.id);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to unlock tag", ex);
            }
        });
        return true;
    }

    public boolean apply(Player player, String name) {
        TagDef def = definition(name);
        if (def == null) return false;
        selected.put(player.getUniqueId(), def.id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO tag_selected (uuid, tag) VALUES (?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET tag = excluded.tag
                        """, player.getUniqueId().toString(), def.id);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to save tag", ex);
            }
        });
        return true;
    }

    public void wipe(UUID uuid) {
        selected.remove(uuid);
        unlocked.remove(uuid);
        try {
            sqlite.execute("DELETE FROM tag_selected WHERE uuid = ?", uuid.toString());
            sqlite.execute("DELETE FROM tag_unlocks WHERE uuid = ?", uuid.toString());
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to wipe tags", ex);
        }
    }

    public boolean canUse(Player player, String name) {
        if (player.hasPermission("shardedcore.tag.admin")) return true;
        if (player.hasPermission("shardedcore.tag." + name)) return true;
        return unlocked(player.getUniqueId()).contains(name.toLowerCase(Locale.ROOT));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) clearTab(event.getPlayer());
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearTab(event.getPlayer());
        UUID uuid = event.getPlayer().getUniqueId();
        selected.remove(uuid);
        unlocked.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean tagsCommand = command.getName().equalsIgnoreCase("tags");
        if (tagsCommand && args.length >= 1 && args[0].equalsIgnoreCase("limited")) {
            return limited(sender, args);
        }
        if (tagsCommand || args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            openGui(player, 0, false);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(sender, args, false);
            case "set" -> set(sender, args);
            case "remove", "delete" -> remove(sender, args);
            case "clear", "off" -> clear(sender);
            case "limited" -> limited(sender, args);
            default -> {
                send(sender, "usage");
                yield true;
            }
        };
    }

    public void open(Player player) {
        openGui(player, 0, false);
    }

    private void openGui(Player player, int page, boolean limited) {
        List<String> ids = limited ? limitedNames() : names();
        List<CosmeticsMenus.Entry> entries = new ArrayList<>();
        for (String id : ids) {
            TagDef def = definition(id, limited);
            if (def == null) continue;
            String color = CosmeticsMenus.colorOf(def.text, cfg("gui.default-color", "0083FF"));
            String title = def.name == null || def.name.isBlank()
                    ? CosmeticsMenus.pretty(def.id)
                    : def.name;
            entries.add(new CosmeticsMenus.Entry(def.id, title,
                    ColorUtil.strip(def.text).isBlank() ? title : ColorUtil.strip(def.text),
                    def.description, color, canUse(player, def.id)));
        }
        CosmeticsMenus.open(plugin, player, config,
                limited ? cfg("gui.limited-title", "☀ Tag ☀ Previewing | Limited Tags") : cfg("gui.title", "☀ Tag ☀ Previewing | Tags"),
                entries, page, "tag", limited,
                next -> openGui(player, next, limited),
                limited ? null : () -> openGui(player, 0, true),
                entry -> {
                    if (!canUse(player, entry.id())) {
                        send(player, "locked", "name", entry.id());
                        sound(player, "sounds.error");
                        return;
                    }
                    apply(player, entry.id());
                    TagDef applied = definition(entry.id(), limited);
                    send(player, "set", "tag", applied == null ? entry.title() : applied.text);
                    sound(player, "sounds.equip");
                    openGui(player, page, limited);
                },
                limited ? () -> openGui(player, 0, false) : null,
                () -> {
                    clear(player);
                    openGui(player, page, limited);
                });
    }

    private boolean create(CommandSender sender, String[] args, boolean limited) {
        if (!admin(sender)) return true;
        if (args.length < 3) {
            send(sender, limited ? "usage-limited" : "usage-create");
            return true;
        }
        String id = sanitize(args[1]);
        String tag = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        if (id.isEmpty() || tag.isBlank()) {
            send(sender, "invalid");
            return true;
        }
        String display = ColorUtil.strip(args[1]).toUpperCase(Locale.ROOT);
        if (display.isBlank()) display = id.toUpperCase(Locale.ROOT);
        String path = (limited ? "limited." : "tags.") + id;
        config.set(path + ".tag", tag);
        config.set(path + ".name", display);
        if (config.getString(path + ".description") == null) {
            config.set(path + ".description", cfg("gui.default-description", "&8Description"));
        }
        Configs.save(config, new File(folder, "config.yml"));
        Perms.ensure("shardedcore.tag." + id);
        send(sender, limited ? "created-limited" : "created", "name", display, "tag", tag);
        return true;
    }

    private boolean limited(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "usage-limited");
            return true;
        }
        return create(sender, args, true);
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
        TagDef def = definition(args[1]);
        if (def == null) {
            send(player, "missing", "name", args[1]);
            return true;
        }
        if (!canUse(player, def.id)) {
            send(player, "locked", "name", def.id);
            return true;
        }
        apply(player, def.id);
        send(player, "set", "tag", def.text);
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
            send(sender, "missing", "name", args[1]);
            return true;
        }
        config.set("tags." + name, null);
        config.set("limited." + name, null);
        Configs.save(config, new File(folder, "config.yml"));
        send(sender, "removed", "name", name);
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
                sqlite.execute("DELETE FROM tag_selected WHERE uuid = ?", player.getUniqueId().toString());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to clear tag", ex);
            }
        });
        send(player, "cleared");
        return true;
    }

    private void clearTab(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(teamName(player));
        if (team != null) team.unregister();
    }

    private String teamName(Player player) {
        String id = player.getUniqueId().toString().replace("-", "");
        return ("sc" + id).substring(0, Math.min(16, 2 + id.length()));
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("shardedcore.tag.admin")) return true;
        send(sender, "no-permission");
        return false;
    }

    private TagDef definition(String name) {
        TagDef regular = definition(name, false);
        return regular == null ? definition(name, true) : regular;
    }

    private TagDef definition(String name, boolean limited) {
        if (name == null || name.isBlank()) return null;
        String root = limited ? "limited" : "tags";
        String id = sanitize(name);
        ConfigurationSection section = config.getConfigurationSection(root + "." + id);
        if (section == null) {
            ConfigurationSection all = config.getConfigurationSection(root);
            if (all != null) {
                for (String key : all.getKeys(false)) {
                    if (key.equalsIgnoreCase(name)) {
                        id = key;
                        section = all.getConfigurationSection(key);
                        break;
                    }
                }
            }
        }
        if (section == null) return null;
        return new TagDef(id, section.getString("tag", section.getString("text", "")),
                section.getString("name", CosmeticsMenus.pretty(id)),
                section.getString("description", cfg("gui.default-description", "&8Description")));
    }

    private List<String> keys(String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? List.of() : new ArrayList<>(section.getKeys(false));
    }

    private void registerAll() {
        for (String name : names()) Perms.ensure("shardedcore.tag." + name);
        for (String name : limitedNames()) Perms.ensure("shardedcore.tag." + name);
    }

    private String selected(UUID uuid) {
        return selected.computeIfAbsent(uuid, id -> {
            try {
                return sqlite.query("SELECT tag FROM tag_selected WHERE uuid = ?", rs -> {
                    try {
                        return rs.next() ? rs.getString("tag") : "";
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
                sqlite.query("SELECT tag FROM tag_unlocks WHERE uuid = ?", rs -> {
                    try {
                        while (rs.next()) set.add(rs.getString("tag"));
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                    return set;
                }, id.toString());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load tag unlocks", ex);
            }
            return set;
        });
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("tags")) {
            if (args.length == 1) return Tabs.filter(List.of("limited"), args[0]);
            if (args.length == 2 && args[0].equalsIgnoreCase("limited")) return Tabs.filter(limitedNames(), args[1]);
            return List.of();
        }
        if (args.length == 1) return Tabs.filter(List.of("create", "set", "remove", "clear", "limited"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove"))) {
            List<String> all = new ArrayList<>(names());
            all.addAll(limitedNames());
            return Tabs.filter(all, args[1]);
        }
        return List.of();
    }

    private record TagDef(String id, String text, String name, String description) {
    }
}

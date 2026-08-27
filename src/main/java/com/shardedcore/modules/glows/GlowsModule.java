package com.shardedcore.modules.glows;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.CosmeticsMenus;
import com.shardedcore.gui.GuiButtons;
import com.shardedcore.gui.Menus;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Items;
import com.shardedcore.util.Perms;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import com.shardedcore.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GlowsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();
    private final Map<UUID, java.util.Set<String>> unlocked = new ConcurrentHashMap<>();

    public GlowsModule(ShardedCore plugin) {
        super(plugin, "glows");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS glow_selected (
                        uuid TEXT PRIMARY KEY,
                        glow TEXT NOT NULL
                    )
                    """);
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS glow_unlocks (
                        uuid TEXT NOT NULL,
                        glow TEXT NOT NULL,
                        PRIMARY KEY (uuid, glow)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create glow tables", ex);
        }
        registerCommand("glows", this);
        registerListener(this);
        for (GlowDef def : effects()) Perms.ensure(def.permission);
        for (Player player : Bukkit.getOnlinePlayers()) applyStored(player);
    }

    @Override
    public void disable() {
        selected.clear();
        unlocked.clear();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length >= 1 && (args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("disable"))) {
            clear(player);
            send(player, "cleared");
            return true;
        }
        openGui(player);
        return true;
    }

    public boolean unlock(UUID uuid, String id) {
        GlowDef def = effect(id);
        if (def == null) return false;
        unlocked(uuid).add(def.id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("INSERT OR IGNORE INTO glow_unlocks (uuid, glow) VALUES (?, ?)", uuid.toString(), def.id);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to unlock glow", ex);
            }
        });
        return true;
    }

    public boolean apply(Player player, String id) {
        GlowDef def = effect(id);
        if (def == null) return false;
        selected.put(player.getUniqueId(), def.id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO glow_selected (uuid, glow) VALUES (?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET glow = excluded.glow
                        """, player.getUniqueId().toString(), def.id);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to save glow", ex);
            }
        });
        applyNow(player, def);
        return true;
    }

    public boolean exists(String id) {
        return effect(id) != null;
    }

    public List<String> names() {
        List<String> names = new ArrayList<>();
        for (GlowDef def : effects()) names.add(def.id);
        return names;
    }

    public boolean canUse(Player player, String id) {
        GlowDef def = effect(id);
        return def != null && canUse(player, def);
    }

    public void open(Player player) {
        openGui(player);
    }

    private void openGui(Player player) {
        int rows = Math.max(3, Math.min(6, config.getInt("menu-rows", config.getInt("gui.rows", 4))));
        String title = cfg("gui.title", GuiButtons.title(cfg("gui.guide", "Glows"), cfg("gui.preview-name", "Glows")));
        Menus.Menu menu = plugin.menus().create(player, title, rows);
        for (GlowDef def : effects()) {
            boolean owned = canUse(player, def);
            String ownedText = owned
                    ? cfg("placeholders.owned-yes", "&#9FFF00&nYes")
                    : cfg("placeholders.owned-no", "&#FF2727&nNo");
            String status = Text.apply(owned
                            ? cfg("gui.status-owned", "&f&lOWNED")
                            : cfg("gui.status-locked", "&f&lLOCKED"),
                    "color", CosmeticsMenus.colorOf(def.color, def.color), "name", def.displayName);
            List<String> template = config.getStringList("gui.lore");
            List<String> lore = Text.applyList(new ArrayList<>(template.isEmpty() ? def.lore : template),
                    "glow_owned_" + def.id, ownedText,
                    "owned", ownedText,
                    "color", CosmeticsMenus.colorOf(def.color, def.color),
                    "status", status,
                    "click", cfg("gui.click-footer", GuiButtons.clickFooter("To Apply")),
                    "name", ColorUtil.strip(def.displayName));
            boolean harnesses = config.getBoolean("gui.colored-harnesses", true);
            ItemStack item = harnesses
                    ? GuiButtons.coloredHarness(def.color, def.displayName, lore)
                    : GuiButtons.coloredBundle(def.color, def.displayName, lore);
            menu.set(def.slot, item, event -> {
                event.setCancelled(true);
                GuiButtons.play(player, "click");
                if (!canUse(player, def)) {
                    send(player, "locked", "name", ColorUtil.strip(def.displayName));
                    sound(player, "sounds.error");
                    GuiButtons.play(player, "error");
                    return;
                }
                if (apply(player, def.id)) {
                    send(player, "set", "name", ColorUtil.strip(def.displayName));
                    sound(player, "sounds.equip");
                    GuiButtons.play(player, "equip");
                }
                openGui(player);
            });
        }
        ConfigurationSection disable = config.getConfigurationSection("disable");
        if (disable != null) {
            menu.set(disable.getInt("slot", 4), Items.fromSection(disable, player), event -> {
                event.setCancelled(true);
                GuiButtons.play(player, "click");
                clear(player);
                send(player, "cleared");
                sound(player, "sounds.click");
                openGui(player);
            });
        }
        menu.set(config.getInt("close.slot", GuiButtons.slot("close", 31)), GuiButtons.close(player), event -> {
            event.setCancelled(true);
            GuiButtons.play(player, "click");
            player.closeInventory();
        });
        GuiButtons.glass(menu, !config.getBoolean("gui.fill", true));
        plugin.menus().open(player, menu);
        sound(player, "sounds.open");
        GuiButtons.play(player, "open");
    }

    private boolean canUse(Player player, GlowDef def) {
        if (player.hasPermission("shardedcore.glow.admin")) return true;
        if (def.permission != null && !def.permission.isBlank() && player.hasPermission(def.permission)) return true;
        if (player.hasPermission("shardedcore.glow." + def.id)) return true;
        if (player.hasPermission("eglow.color." + def.id)) return true;
        return unlocked(player.getUniqueId()).contains(def.id);
    }

    private void applyStored(Player player) {
        String id = selected.computeIfAbsent(player.getUniqueId(), uuid -> {
            try {
                return sqlite.query("SELECT glow FROM glow_selected WHERE uuid = ?", rs -> {
                    try {
                        return rs.next() ? rs.getString("glow") : "";
                    } catch (SQLException ex) {
                        return "";
                    }
                }, uuid.toString());
            } catch (SQLException ex) {
                return "";
            }
        });
        GlowDef def = effect(id);
        if (def != null) applyNow(player, def);
    }

    private void applyNow(Player player, GlowDef def) {
        if (def.command != null && !def.command.isBlank()) {
            player.performCommand(stripSlash(def.command));
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, true));
    }

    public void clear(Player player) {
        selected.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.GLOWING);
        String disable = cfg("disable-command", "eglow:eglow disabled");
        if (disable != null && !disable.isBlank()) player.performCommand(stripSlash(disable));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM glow_selected WHERE uuid = ?", player.getUniqueId().toString());
            } catch (SQLException ignored) {
            }
        });
    }

    public void wipe(UUID uuid) {
        selected.remove(uuid);
        unlocked.remove(uuid);
        try {
            sqlite.execute("DELETE FROM glow_selected WHERE uuid = ?", uuid.toString());
            sqlite.execute("DELETE FROM glow_unlocks WHERE uuid = ?", uuid.toString());
        } catch (SQLException ignored) {
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            String disable = cfg("disable-command", "eglow:eglow disabled");
            if (disable != null && !disable.isBlank()) player.performCommand(stripSlash(disable));
        }
    }

    private List<GlowDef> effects() {
        List<GlowDef> list = new ArrayList<>();
        ConfigurationSection root = config.getConfigurationSection("glows");
        if (root == null) return list;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;
            list.add(new GlowDef(
                    id.toLowerCase(Locale.ROOT),
                    section.getInt("slot", 0),
                    section.getString("permission", "eglow.color." + id),
                    section.getString("command", "eglow:eglow " + id),
                    section.getString("color", "#FFFFFF"),
                    section.getString("display-name", "&f&l" + id.toUpperCase(Locale.ROOT) + " GLOW"),
                    section.getStringList("lore")
            ));
        }
        return list;
    }

    private GlowDef effect(String id) {
        if (id == null || id.isBlank()) return null;
        for (GlowDef def : effects()) {
            if (def.id.equalsIgnoreCase(id)) return def;
        }
        return null;
    }

    private java.util.Set<String> unlocked(UUID uuid) {
        return unlocked.computeIfAbsent(uuid, id -> {
            java.util.Set<String> set = ConcurrentHashMap.newKeySet();
            try {
                sqlite.query("SELECT glow FROM glow_unlocks WHERE uuid = ?", rs -> {
                    try {
                        while (rs.next()) set.add(rs.getString("glow"));
                    } catch (SQLException ex) {
                        throw new IllegalStateException(ex);
                    }
                    return set;
                }, id.toString());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load glow unlocks", ex);
            }
            return set;
        });
    }

    private static String stripSlash(String command) {
        String value = command == null ? "" : command.trim();
        return value.startsWith("/") ? value.substring(1) : value;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) applyStored(event.getPlayer());
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        selected.remove(event.getPlayer().getUniqueId());
        unlocked.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Tabs.filter(List.of("clear", "disable"), args[0]);
        return List.of();
    }

    private record GlowDef(String id, int slot, String permission, String command, String color,
                           String displayName, List<String> lore) {
    }
}

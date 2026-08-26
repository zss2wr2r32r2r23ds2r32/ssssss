package com.shardedcore.modules.glows;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.gui.CosmeticsMenus;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Perms;
import com.shardedcore.util.Tabs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class GlowsModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private Sqlite sqlite;
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();

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
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create glow table", ex);
        }
        registerCommand("glows", this);
        registerListener(this);
        for (GlowDef def : effects()) Perms.ensure("shardedcore.glow." + def.id);
        for (Player player : Bukkit.getOnlinePlayers()) applyStored(player);
    }

    @Override
    public void disable() {
        selected.clear();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("clear")) {
            clear(player);
            send(player, "cleared");
            return true;
        }
        openGui(player, 0);
        return true;
    }

    private void openGui(Player player, int page) {
        List<CosmeticsMenus.Entry> entries = new ArrayList<>();
        for (GlowDef def : effects()) {
            Perms.ensure("shardedcore.glow." + def.id);
            String color = ColorUtil.colorCode(cfg("gui.default-color", "0083FF"));
            String title = CosmeticsMenus.pretty(def.display).toUpperCase(Locale.ROOT);
            entries.add(new CosmeticsMenus.Entry(def.id, title, title + " Glow",
                    cfg("gui.default-description", "&8Description"), color, canUse(player, def.id)));
        }
        CosmeticsMenus.open(plugin, player, config, cfg("gui.title", "&8Glows"),
                entries, page, "Glow", false,
                next -> openGui(player, next),
                null,
                entry -> {
                    if (!canUse(player, entry.id())) {
                        send(player, "locked", "name", entry.id());
                        return;
                    }
                    if (apply(player, entry.id())) send(player, "set", "name", entry.title());
                    openGui(player, page);
                });
    }

    private boolean canUse(Player player, String id) {
        return player.hasPermission("shardedcore.glow.admin")
                || player.hasPermission("shardedcore.glow." + id);
    }

    private boolean apply(Player player, String id) {
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
        if (!enableEglow(player, def)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        }
    }

    private void clear(Player player) {
        selected.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.GLOWING);
        disableEglow(player);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("DELETE FROM glow_selected WHERE uuid = ?", player.getUniqueId().toString());
            } catch (SQLException ignored) {
            }
        });
    }

    public void wipe(UUID uuid) {
        selected.remove(uuid);
        try {
            sqlite.execute("DELETE FROM glow_selected WHERE uuid = ?", uuid.toString());
        } catch (SQLException ignored) {
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.removePotionEffect(PotionEffectType.GLOWING);
            disableEglow(player);
        }
    }

    private List<GlowDef> effects() {
        List<GlowDef> list = new ArrayList<>();
        Object api = eglowApi();
        if (api == null) {
            list.add(new GlowDef("glow", "Glow", null));
            return list;
        }
        try {
            Method method = api.getClass().getMethod("getEGlowEffects");
            Object raw = method.invoke(api);
            if (raw instanceof Collection<?> collection) {
                for (Object effect : collection) {
                    String name = String.valueOf(effect.getClass().getMethod("getName").invoke(effect));
                    list.add(new GlowDef(sanitize(name), name, effect));
                }
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Could not list eGlow effects", ex);
        }
        if (list.isEmpty()) list.add(new GlowDef("glow", "Glow", null));
        return list;
    }

    private GlowDef effect(String id) {
        if (id == null || id.isBlank()) return null;
        for (GlowDef def : effects()) {
            if (def.id.equalsIgnoreCase(id) || def.display.equalsIgnoreCase(id)) return def;
        }
        return null;
    }

    private Object eglowApi() {
        Plugin eglow = Bukkit.getPluginManager().getPlugin("eGlow");
        if (eglow == null || !eglow.isEnabled()) return null;
        try {
            return eglow.getClass().getMethod("getAPI").invoke(eglow);
        } catch (Throwable ex) {
            return null;
        }
    }

    private boolean enableEglow(Player player, GlowDef def) {
        Object api = eglowApi();
        if (api == null || def.effect == null) return false;
        try {
            Object glowPlayer = api.getClass().getMethod("getEGlowPlayer", Player.class).invoke(api, player);
            if (glowPlayer == null) return false;
            try {
                glowPlayer.getClass().getMethod("disableGlow", boolean.class).invoke(glowPlayer, true);
            } catch (Throwable ignored) {
            }
            for (Method method : glowPlayer.getClass().getMethods()) {
                if (!method.getName().equals("enableGlow") || method.getParameterCount() != 1) continue;
                method.invoke(glowPlayer, def.effect);
                return true;
            }
        } catch (Throwable ex) {
            plugin.getLogger().log(Level.WARNING, "Could not enable eGlow", ex);
        }
        return false;
    }

    private void disableEglow(Player player) {
        Object api = eglowApi();
        if (api == null) return;
        try {
            Object glowPlayer = api.getClass().getMethod("getEGlowPlayer", Player.class).invoke(api, player);
            if (glowPlayer != null) {
                glowPlayer.getClass().getMethod("disableGlow", boolean.class).invoke(glowPlayer, true);
            }
        } catch (Throwable ignored) {
        }
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
    }

    private static String sanitize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Tabs.filter(List.of("clear"), args[0]);
        return List.of();
    }

    private record GlowDef(String id, String display, Object effect) {
    }
}

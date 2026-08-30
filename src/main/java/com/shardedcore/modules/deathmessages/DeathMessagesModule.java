package com.shardedcore.modules.deathmessages;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.modules.combat.CombatModule;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DeathMessagesModule extends Module implements Listener {

    private final Map<UUID, Damager> last = new ConcurrentHashMap<>();

    public DeathMessagesModule(ShardedCore plugin) {
        super(plugin, "deathmessages");
    }

    @Override
    public void enable() {
        registerListener(this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Entity damager = event.getDamager();
        if (damager instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            damager = shooter;
        }
        last.put(player.getUniqueId(), new Damager(damager.getUniqueId(), damager instanceof Player, damager.getName(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.deathMessage(null);
        Player player = event.getEntity();
        EntityDamageEvent.DamageCause cause = player.getLastDamageCause() == null
                ? EntityDamageEvent.DamageCause.CUSTOM
                : player.getLastDamageCause().getCause();
        Damager damager = last.get(player.getUniqueId());
        long tagMs = config.getLong("combat-tag-seconds", 10) * 1000L;
        boolean recent = damager != null && System.currentTimeMillis() - damager.at <= tagMs;
        CombatModule combat = plugin.modules().get(CombatModule.class);
        if (!recent && combat != null && combat.tagged(player) && damager != null) recent = true;
        String category;
        String killer;
        String symbol;
        if (recent && damager != null && damager.player) {
            category = "by-player";
            killer = damager.name;
            symbol = cfg("symbols.by-player", "🗡");
        } else if (recent && damager != null) {
            category = "by-mob";
            killer = damager.name;
            symbol = cfg("symbols.by-mob", "🏹");
        } else {
            category = "environment";
            killer = "";
            symbol = cfg("symbols.environment", "☀");
        }
        String path = category + "." + cause.name().toLowerCase().replace('_', '-');
        String line = cfg(path, cfg(category + ".default", ""));
        if (line == null || line.isBlank()) return;
        line = line.replace("%player%", player.getName())
                .replace("%killer%", killer)
                .replace("%symbol%", symbol);
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (settings != null && !settings.death(viewer)) continue;
            viewer.sendMessage(ColorUtil.parse(line));
        }
        last.remove(player.getUniqueId());
    }

    private record Damager(UUID uuid, boolean player, String name, long at) {
    }
}

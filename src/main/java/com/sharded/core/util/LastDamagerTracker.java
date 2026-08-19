package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the last player who damaged another player (for PvP graves when getKiller() is null). */
public final class LastDamagerTracker implements Listener {

    private static LastDamagerTracker instance;

    private final Map<UUID, UUID> lastDamager = new ConcurrentHashMap<>();
    /** Whether the attacker had graves permission at damage time (survives killer going offline). */
    private final Map<UUID, Boolean> lastDamagerGravePerm = new ConcurrentHashMap<>();

    public static LastDamagerTracker register(com.sharded.core.ShardedCore plugin) {
        if (instance == null) {
            instance = new LastDamagerTracker();
            plugin.getServer().getPluginManager().registerEvents(instance, plugin);
        }
        return instance;
    }

    public static LastDamagerTracker get() {
        return instance;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null && !attacker.equals(victim)) {
            lastDamager.put(victim.getUniqueId(), attacker.getUniqueId());
            lastDamagerGravePerm.put(victim.getUniqueId(), attacker.hasPermission("sharded.graves.use"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        UUID victimId = event.getEntity().getUniqueId();
        // Delay cleanup so HIGH-priority death handlers can still read the killer.
        Bukkit.getScheduler().runTask(
                com.sharded.core.ShardedCore.get(),
                () -> {
                    lastDamager.remove(victimId);
                    lastDamagerGravePerm.remove(victimId);
                });
    }

    public UUID killerIdOf(Player victim) {
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) return killer.getUniqueId();
        return lastDamager.get(victim.getUniqueId());
    }

    public boolean killerHadGravePermission(Player victim) {
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return killer.hasPermission("sharded.graves.use");
        }
        Boolean stored = lastDamagerGravePerm.get(victim.getUniqueId());
        if (stored != null) return stored;
        UUID id = lastDamager.get(victim.getUniqueId());
        if (id == null) return false;
        Player damager = Bukkit.getPlayer(id);
        return damager != null && damager.hasPermission("sharded.graves.use");
    }

    public Player killerOf(Player victim) {
        UUID id = killerIdOf(victim);
        if (id == null || id.equals(victim.getUniqueId())) return null;
        Player damager = Bukkit.getPlayer(id);
        return damager != null && damager.isOnline() ? damager : null;
    }

    private Player resolveAttacker(org.bukkit.entity.Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}

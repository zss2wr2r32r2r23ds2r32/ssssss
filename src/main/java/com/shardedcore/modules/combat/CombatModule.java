package com.shardedcore.modules.combat;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatModule extends Module implements Listener {

    private final Map<UUID, Long> tagged = new ConcurrentHashMap<>();
    private BukkitTask task;

    public CombatModule(ShardedCore plugin) {
        super(plugin, "combat");
    }

    @Override
    public void enable() {
        registerListener(this);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    @Override
    public void disable() {
        if (task != null) task.cancel();
        tagged.clear();
        cleanup();
    }

    public boolean tagged(Player player) {
        Long until = tagged.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private void tag(Player player) {
        tagged.put(player.getUniqueId(), System.currentTimeMillis() + config.getInt("tag-seconds", 10) * 1000L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getDamager() instanceof EnderPearl) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player player) attacker = player;
        else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile instanceof EnderPearl || !config.getBoolean("enderpearls-tag", false) && projectile instanceof EnderPearl) {
                return;
            }
            if (projectile instanceof EnderPearl) return;
            if (projectile.getShooter() instanceof Player player) attacker = player;
        }
        if (attacker == null || attacker.equals(victim)) return;
        tag(attacker);
        tag(victim);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        tagged.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean in = tagged(player);
        tagged.remove(player.getUniqueId());
        if (in && config.getBoolean("kill-on-logout", true)) player.setHealth(0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!tagged(player) || player.hasPermission("shardedcore.combat.bypass")) return;
        event.setCancelled(true);
        send(player, "blocked-command");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = tagged.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (entry.getValue() <= now) {
                iterator.remove();
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            long left = Math.max(1, (entry.getValue() - now + 999) / 1000);
            player.sendActionBar(ColorUtil.parse(cfg("actionbar", "").replace("%seconds%", String.valueOf(left))));
        }
    }
}

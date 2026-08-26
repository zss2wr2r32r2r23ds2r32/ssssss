package com.shardedcore.modules.combat;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Text;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class CombatModule extends Module implements Listener {

    private final Map<UUID, Long> taggedUntil = new HashMap<>();
    private BukkitTask tickTask;

    public CombatModule(ShardedCore plugin) {
        super(plugin, "combat");
    }

    @Override
    public void enable() {
        registerListener(this);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    @Override
    public void disable() {
        if (tickTask != null) tickTask.cancel();
        taggedUntil.clear();
        cleanup();
    }

    public boolean isTagged(Player player) {
        Long until = taggedUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private int tagSeconds() {
        return config.getInt("tag-seconds", 15);
    }

    private void tag(Player player) {
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + tagSeconds() * 1000L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (event.getDamager() instanceof EnderPearl) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player player) {
            attacker = player;
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile instanceof EnderPearl) return;
            if (projectile.getShooter() instanceof Player player) {
                attacker = player;
            }
        }
        if (attacker == null) return;

        tag(attacker);
        tag(victim);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        taggedUntil.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean tagged = isTagged(player);
        taggedUntil.remove(player.getUniqueId());
        if (!tagged) return;
        if (!config.getBoolean("kill-on-logout", true)) return;
        player.setHealth(0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isTagged(player)) return;
        if (player.hasPermission("shardedcore.combat.commandbypass")) return;
        event.setCancelled(true);
        send(player, "no-commands");
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = taggedUntil.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (entry.getValue() <= now) {
                it.remove();
                continue;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            long left = (entry.getValue() - now) / 1000L + 1;
            String msg = config.getString("actionbar",
                            "&#FF0000&lCOMBAT &8▷ &rYou are in combat for &#FF0000&n%seconds%&r&#FF0000s")
                    .replace("%seconds%", String.valueOf(left));
            player.sendActionBar(Text.c(msg));
        }
    }
}

package dev.shardedsmp.listener;

import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.game.EnchantManager;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

public class CombatListener implements Listener {
    private final ShardedSMP plugin;

    public CombatListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player damager = damager(event);
        if (damager == null) {
            return;
        }
        if (event.getEntity() instanceof Player && plugin.game().graceActive()) {
            event.setCancelled(true);
            return;
        }
        ItemStack weapon = damager.getInventory().getItemInMainHand();
        if (EnchantManager.netheriteOnly(plugin.game().phase())
                && EnchantManager.isRestrictedTool(weapon.getType())
                && event.getEntity() instanceof LivingEntity) {
            event.setCancelled(true);
            damager.sendMessage(dev.shardedsmp.util.ColorUtil.color("&cOnly netherite tools can be used in this phase."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getNewSlot());
        if (item == null) {
            return;
        }
        plugin.listenerEnchant().cap(item);
        if (EnchantManager.netheriteOnly(plugin.game().phase()) && EnchantManager.isRestrictedTool(item.getType())) {
            event.getPlayer().sendActionBar(dev.shardedsmp.util.ColorUtil.color("&cNetherite tools only this phase."));
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        plugin.killStreaks().reset(victim);
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            int streak = plugin.killStreaks().addKill(killer);
            if (streak == 3 || streak == 5 || streak == 10) {
                killer.sendMessage(dev.shardedsmp.util.ColorUtil.color("&fKill streak &#FF0000" + streak + " &funlocked."));
            }
        }
    }

    private Player damager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof ThrownPotion potion && potion.getShooter() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}

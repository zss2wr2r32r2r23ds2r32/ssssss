package dev.shardedsmp.listener;

import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.util.Keys;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wither;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BossListener implements Listener {
    private final ShardedSMP plugin;
    private final Map<UUID, Double> dragonDamage = new HashMap<>();

    public BossListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player player = playerDamager(event);
        if (player == null) {
            return;
        }
        if (event.getEntity() instanceof EnderDragon) {
            dragonDamage.merge(player.getUniqueId(), event.getFinalDamage(), Double::sum);
        }
        if (event.getEntity() instanceof Wither wither && isEventWither(wither)) {
            wither.setAI(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Wither wither && isEventWither(wither)) {
            event.getDrops().add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            event.getDrops().add(new ItemStack(Material.WIND_CHARGE, 16));
            event.getDrops().add(new ItemStack(Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
            event.getDrops().add(new ItemStack(Material.TOTEM_OF_UNDYING, 1));
            plugin.game().onEventWitherKilled();
            return;
        }
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        plugin.game().onDragonKilled();
        Location dropAt = dragon.getLocation();
        event.getDrops().add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3));
        event.getDrops().add(new ItemStack(Material.WIND_CHARGE, 64));
        event.getDrops().add(new ItemStack(Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, 1));
        event.getDrops().add(new ItemStack(Material.TOTEM_OF_UNDYING, 4));

        Player top = topDamager();
        dragonDamage.clear();
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        if (top != null && top.isOnline()) {
            HashMap<Integer, ItemStack> overflow = top.getInventory().addItem(egg);
            if (!overflow.isEmpty()) {
                dropAt.getWorld().dropItemNaturally(top.getLocation(), egg);
            }
            top.sendMessage(dev.shardedsmp.util.ColorUtil.color("&fYou dealt the most damage to the dragon and received the &#FF0000Dragon Egg&f."));
            plugin.game().updateDragonEggHearts(top);
        } else {
            dropAt.getWorld().dropItemNaturally(dropAt, egg);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeVanillaEgg(dropAt), 40L);
    }

    private void removeVanillaEgg(Location around) {
        if (around.getWorld() == null) {
            return;
        }
        Location portal = around.getWorld().getSpawnLocation();
        for (int x = -6; x <= 6; x++) {
            for (int y = -4; y <= 8; y++) {
                for (int z = -6; z <= 6; z++) {
                    Location check = portal.clone().add(x, y, z);
                    if (check.getBlock().getType() == Material.DRAGON_EGG) {
                        check.getBlock().setType(Material.AIR);
                    }
                }
            }
        }
    }

    private Player topDamager() {
        UUID best = null;
        double amount = 0;
        for (Map.Entry<UUID, Double> entry : dragonDamage.entrySet()) {
            if (entry.getValue() > amount) {
                amount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best == null ? null : plugin.getServer().getPlayer(best);
    }

    private boolean isEventWither(Wither wither) {
        return wither.getPersistentDataContainer().has(Keys.eventWither, PersistentDataType.BOOLEAN)
                || plugin.game().isEventWither(wither.getUniqueId());
    }

    private Player playerDamager(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Tameable tameable && tameable.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }
}

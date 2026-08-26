package com.sharded.core.modules.pickupmobs;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Sneak + right-click allowed mobs to pick them up as spawn eggs (config list). */
public final class PickupMobsModule extends Module {

    private NamespacedKey mobKey;
    private Set<EntityType> allowed = new HashSet<>();

    public PickupMobsModule(ShardedCore plugin) {
        super(plugin, "pickupmobs");
    }

    @Override
    protected void onEnable() {
        mobKey = new NamespacedKey(plugin, "picked_mob");
        reloadAllowed();
    }

    private void reloadAllowed() {
        allowed.clear();
        for (String raw : config.getStringList("allowed-mobs")) {
            try {
                allowed.add(EntityType.valueOf(raw.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[pickupmobs] Unknown mob: " + raw);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (!player.hasPermission("sharded.pickupmobs.use")) return;

        Entity entity = event.getRightClicked();
        if (!(entity instanceof LivingEntity living) || entity instanceof Player) return;
        if (!allowed.contains(entity.getType())) return;

        Material egg;
        try {
            egg = Material.valueOf(entity.getType().name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException ex) {
            return;
        }

        ItemStack item = new ItemBuilder(egg)
                .name("&f" + entity.getType().name().replace('_', ' ') + " &7(Mob)")
                .build();
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, entity.getType().name());
        item.setItemMeta(meta);

        entity.remove();
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        send(player, "picked-up", "%mob%", entity.getType().name().replace('_', ' '));
    }
}

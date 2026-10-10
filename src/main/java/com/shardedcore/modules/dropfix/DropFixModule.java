package com.shardedcore.modules.dropfix;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DropFixModule extends Module implements Listener {

    public DropFixModule(ShardedCore plugin) {
        super(plugin, "dropfix");
    }

    @Override
    public void enable() {
        registerListener(this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!config.getBoolean("players", true)) return;
        restack(event.getDrops());
        Location dropAt = dropLocation(event.getEntity().getLocation());
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        spawn(dropAt, drops);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player) return;
        if (!config.getBoolean("mobs", true)) return;
        restack(event.getDrops());
        Location dropAt = dropLocation(event.getEntity().getLocation());
        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        spawn(dropAt, drops);
    }

    private void restack(List<ItemStack> drops) {
        if (!config.getBoolean("merge-stacks", true)) return;
        for (int i = 0; i < drops.size(); i++) {
            ItemStack current = drops.get(i);
            if (current == null || current.getType().isAir()) continue;
            for (int j = i + 1; j < drops.size(); j++) {
                ItemStack other = drops.get(j);
                if (other == null || !current.isSimilar(other)) continue;
                int space = current.getMaxStackSize() - current.getAmount();
                if (space <= 0) continue;
                int move = Math.min(space, other.getAmount());
                current.setAmount(current.getAmount() + move);
                other.setAmount(other.getAmount() - move);
            }
        }
        Iterator<ItemStack> iterator = drops.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) iterator.remove();
        }
    }

    private Location dropLocation(Location origin) {
        if (!config.getBoolean("center-on-block", true)) return origin;
        return origin.getBlock().getLocation().add(0.5, 0.2, 0.5);
    }

    private void spawn(Location location, List<ItemStack> drops) {
        if (location.getWorld() == null) return;
        for (ItemStack stack : drops) {
            Item item = location.getWorld().dropItem(location, stack);
            item.setVelocity(new Vector(0, 0.05, 0));
        }
    }
}

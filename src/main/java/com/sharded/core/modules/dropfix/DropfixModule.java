package com.sharded.core.modules.dropfix;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Merges and centers item drops from players and mobs. */
public final class DropfixModule extends Module {

    public DropfixModule(ShardedCore plugin) {
        super(plugin, "dropfix");
    }

    @Override
    protected void onEnable() {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!shouldHandle(event.getEntity() instanceof Player)) return;
        List<ItemStack> drops = event.getDrops();
        if (drops.isEmpty()) return;

        if (config.getBoolean("merge-stacks", true)) {
            event.getDrops().clear();
            event.getDrops().addAll(mergeStacks(drops));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!config.getBoolean("enabled", true)) return;
        Item item = event.getEntity();
        boolean fromPlayer = item.getThrower() != null
                && plugin.getServer().getPlayer(item.getThrower()) != null;
        if (!shouldHandle(fromPlayer || isMobDrop(item))) return;

        if (config.getBoolean("center-on-block", true)) {
            Location centered = centerOnBlock(item.getLocation());
            item.teleport(centered);
        }

        if (!config.getBoolean("merge-stacks", true)) return;

        double radius = config.getDouble("merge-radius", 1.5D);
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType().isAir()) return;

        for (Item nearby : item.getNearbyEntities(radius, radius, radius).stream()
                .filter(e -> e instanceof Item)
                .map(e -> (Item) e)
                .toList()) {
            if (nearby.equals(item)) continue;
            ItemStack other = nearby.getItemStack();
            if (!canMerge(stack, other)) continue;
            int max = stack.getMaxStackSize();
            int space = max - stack.getAmount();
            if (space <= 0) break;
            int move = Math.min(space, other.getAmount());
            stack.setAmount(stack.getAmount() + move);
            other.setAmount(other.getAmount() - move);
            item.setItemStack(stack);
            if (other.getAmount() <= 0) nearby.remove();
        }
    }

    private boolean shouldHandle(boolean playerContext) {
        if (playerContext) return config.getBoolean("player-drops", true);
        return config.getBoolean("mob-drops", true);
    }

    private boolean isMobDrop(Item item) {
        return item.getThrower() == null && item.getOwner() == null;
    }

    private static Location centerOnBlock(Location location) {
        Location centered = location.clone();
        centered.setX(Math.floor(centered.getX()) + 0.5D);
        centered.setY(Math.floor(centered.getY()) + 0.0625D);
        centered.setZ(Math.floor(centered.getZ()) + 0.5D);
        return centered;
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> drops) {
        Map<String, ItemStack> merged = new HashMap<>();
        List<ItemStack> ordered = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType().isAir()) continue;
            String key = stackKey(stack);
            ItemStack existing = merged.get(key);
            if (existing == null) {
                ItemStack copy = stack.clone();
                merged.put(key, copy);
                ordered.add(copy);
                continue;
            }
            int max = existing.getMaxStackSize();
            int add = stack.getAmount();
            int space = max - existing.getAmount();
            if (space > 0) {
                int moved = Math.min(space, add);
                existing.setAmount(existing.getAmount() + moved);
                add -= moved;
            }
            while (add > 0) {
                ItemStack overflow = stack.clone();
                int amount = Math.min(max, add);
                overflow.setAmount(amount);
                ordered.add(overflow);
                merged.put(key + "#" + ordered.size(), overflow);
                add -= amount;
            }
        }
        return ordered;
    }

    private static boolean canMerge(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType().isAir() || b.getType().isAir()) return false;
        return a.isSimilar(b);
    }

    private static String stackKey(ItemStack stack) {
        StringBuilder sb = new StringBuilder(stack.getType().name());
        if (stack.hasItemMeta()) {
            sb.append(':').append(stack.getItemMeta().hashCode());
        }
        return sb.toString();
    }
}

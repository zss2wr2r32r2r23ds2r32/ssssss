package com.shardedcore.modules.dropfix;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.event.Listener;
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
import java.util.List;
import java.util.Map;

public final class DropfixModule extends Module implements Listener {

    public DropfixModule(ShardedCore plugin) { super(plugin, "dropfix"); }

    @Override public void enable() { registerListener(this); }
    @Override public void disable() { cleanup(); }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!shouldHandle(event.getEntity() instanceof Player)) return;
        if (config.getBoolean("merge-stacks", true)) {
            List<ItemStack> merged = mergeStacks(event.getDrops());
            event.getDrops().clear();
            event.getDrops().addAll(merged);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!config.getBoolean("enabled", true)) return;
        Item item = event.getEntity();
        boolean fromPlayer = item.getThrower() != null && plugin.getServer().getPlayer(item.getThrower()) != null;
        if (!shouldHandle(fromPlayer || item.getThrower() == null)) return;
        if (config.getBoolean("center-on-block", true)) item.teleport(centerOnBlock(item.getLocation()));
        if (!config.getBoolean("merge-stacks", true)) return;
        double radius = config.getDouble("merge-radius", 1.5D);
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getType().isAir()) return;
        for (Item nearby : item.getNearbyEntities(radius, radius, radius).stream().filter(e -> e instanceof Item).map(e -> (Item) e).toList()) {
            if (nearby.equals(item)) continue;
            ItemStack other = nearby.getItemStack();
            if (!stack.isSimilar(other)) continue;
            int space = stack.getMaxStackSize() - stack.getAmount();
            if (space <= 0) break;
            int move = Math.min(space, other.getAmount());
            stack.setAmount(stack.getAmount() + move);
            other.setAmount(other.getAmount() - move);
            item.setItemStack(stack);
            if (other.getAmount() <= 0) nearby.remove();
        }
    }

    private boolean shouldHandle(boolean playerContext) {
        return playerContext ? config.getBoolean("player-drops", true) : config.getBoolean("mob-drops", true);
    }

    private static Location centerOnBlock(Location location) {
        Location c = location.clone();
        c.setX(Math.floor(c.getX()) + 0.5D);
        c.setY(Math.floor(c.getY()) + 0.0625D);
        c.setZ(Math.floor(c.getZ()) + 0.5D);
        return c;
    }

    private static List<ItemStack> mergeStacks(List<ItemStack> drops) {
        Map<String, ItemStack> merged = new HashMap<>();
        List<ItemStack> ordered = new ArrayList<>();
        for (ItemStack stack : drops) {
            if (stack == null || stack.getType().isAir()) continue;
            String key = stack.getType().name() + (stack.hasItemMeta() ? stack.getItemMeta().hashCode() : 0);
            ItemStack existing = merged.get(key);
            if (existing == null) { ItemStack copy = stack.clone(); merged.put(key, copy); ordered.add(copy); continue; }
            int space = existing.getMaxStackSize() - existing.getAmount();
            int add = stack.getAmount();
            if (space > 0) { int moved = Math.min(space, add); existing.setAmount(existing.getAmount() + moved); add -= moved; }
            while (add > 0) {
                ItemStack overflow = stack.clone();
                overflow.setAmount(Math.min(existing.getMaxStackSize(), add));
                ordered.add(overflow);
                add -= overflow.getAmount();
            }
        }
        return ordered;
    }
}

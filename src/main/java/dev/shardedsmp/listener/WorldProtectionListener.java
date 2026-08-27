package dev.shardedsmp.listener;

import dev.shardedsmp.ShardedSMP;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.world.PortalCreateEvent;

public class WorldProtectionListener implements Listener {
    private final ShardedSMP plugin;

    public WorldProtectionListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!plugin.game().graceActive()) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity) || event.getEntity() instanceof Player) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onForm(BlockFormEvent event) {
        if (event.getNewState().getType() == Material.OBSIDIAN) {
            event.getNewState().setType(Material.COBBLESTONE);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onFromTo(BlockFromToEvent event) {
        Block to = event.getToBlock();
        Material from = event.getBlock().getType();
        if ((from == Material.LAVA || from == Material.WATER)
                && wouldCreateObsidian(from, to)) {
            event.setCancelled(true);
            if (to.getType() == Material.WATER || to.getType() == Material.LAVA || to.getType().isAir()) {
                to.setType(Material.COBBLESTONE);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBucket(PlayerBucketEmptyEvent event) {
        Block block = event.getBlock();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (block.getType() == Material.OBSIDIAN) {
                block.setType(Material.COBBLESTONE);
            }
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreakObsidian(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.OBSIDIAN) {
            return;
        }
        event.setDropItems(false);
        event.setExpToDrop(0);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.FIRE && !plugin.game().netherOpen()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerPortal(PlayerPortalEvent event) {
        World.Environment destination = event.getTo() == null || event.getTo().getWorld() == null
                ? null : event.getTo().getWorld().getEnvironment();
        if (destination == World.Environment.NETHER && !plugin.game().netherOpen()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(dev.shardedsmp.util.ColorUtil.color("&cThe Nether has not opened yet."));
        }
        if (destination == World.Environment.THE_END && !plugin.game().endOpen()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(dev.shardedsmp.util.ColorUtil.color("&cThe End has not opened yet."));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEntityPortal(EntityPortalEvent event) {
        World.Environment destination = event.getTo() == null || event.getTo().getWorld() == null
                ? null : event.getTo().getWorld().getEnvironment();
        if (destination == World.Environment.NETHER && !plugin.game().netherOpen()) {
            event.setCancelled(true);
        }
        if (destination == World.Environment.THE_END && !plugin.game().endOpen()) {
            event.setCancelled(true);
        }
    }

    private boolean wouldCreateObsidian(Material from, Block to) {
        if (from == Material.LAVA && to.getType() == Material.WATER) {
            return true;
        }
        return from == Material.WATER && to.getType() == Material.LAVA;
    }
}

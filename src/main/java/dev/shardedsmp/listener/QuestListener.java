package dev.shardedsmp.listener;

import dev.shardedsmp.GamePhase;
import dev.shardedsmp.ShardedSMP;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class QuestListener implements Listener {
    private final ShardedSMP plugin;

    public QuestListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isDiamondOre(block.getType())) {
            return;
        }
        if (plugin.game().isPlayerPlacedDiamondOre(block.getLocation())) {
            plugin.game().removePlacedDiamondOre(block.getLocation());
            return;
        }
        if (plugin.game().phase().number() < GamePhase.PHASE_3.number() || plugin.game().endOpen()) {
            return;
        }
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        plugin.game().addDiamond();
        event.getPlayer().sendMessage(dev.shardedsmp.util.ColorUtil.color("&b+1 Community Diamond &7("
                + plugin.game().diamondsMined() + "/" + plugin.game().diamondsNeeded() + ")"));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlaceOre(BlockPlaceEvent event) {
        if (isDiamondOre(event.getBlock().getType())) {
            plugin.game().markPlacedDiamondOre(event.getBlock().getLocation());
        }
    }

    private boolean isDiamondOre(Material material) {
        return material == Material.DIAMOND_ORE || material == Material.DEEPSLATE_DIAMOND_ORE;
    }
}

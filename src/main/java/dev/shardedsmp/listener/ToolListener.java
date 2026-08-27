package dev.shardedsmp.listener;

import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.game.EnchantManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ToolListener implements Listener {
    private final ShardedSMP plugin;

    public ToolListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        if (!EnchantManager.netheriteOnly(plugin.game().phase())) {
            return;
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (EnchantManager.isRestrictedTool(tool.getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(dev.shardedsmp.util.ColorUtil.color("&cOnly netherite tools can be used in this phase."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || !EnchantManager.netheriteOnly(plugin.game().phase())) {
            return;
        }
        if (EnchantManager.isRestrictedTool(item.getType()) && event.getClickedBlock() != null) {
            switch (event.getAction()) {
                case LEFT_CLICK_BLOCK, RIGHT_CLICK_BLOCK -> {
                    // Block breaking is cancelled separately; still warn on interact.
                }
                default -> {
                }
            }
        }
        plugin.listenerEnchant().cap(item);
    }
}

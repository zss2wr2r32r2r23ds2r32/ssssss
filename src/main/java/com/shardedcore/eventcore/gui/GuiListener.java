package com.shardedcore.eventcore.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * The one and only inventory listener in the plugin.
 *
 * <p>Routing by {@link InventoryHolder} identity means adding a new menu costs
 * no extra event handler, and the fast path for a normal chest or player
 * inventory is a single {@code instanceof} check.</p>
 */
public final class GuiListener implements Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Gui gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        boolean clickedMenu = event.getClickedInventory() == event.getView().getTopInventory();
        if (!gui.allowsItemMovement()) {
            // Also blocks shift-clicking and number-key swaps from the bottom inventory.
            event.setCancelled(true);
        }

        if (!clickedMenu) {
            if (!gui.allowsItemMovement() && event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return;
        }
        gui.dispatch(player, event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui && !gui.allowsItemMovement()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui && event.getPlayer() instanceof Player player) {
            gui.onClose(player);
        }
    }
}

package com.shardedcore.gui;

import com.shardedcore.util.TrackedInventories;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    private final GuiManager manager;

    public GuiListener(GuiManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = TrackedInventories.lookup(event.getView().getTopInventory());
        if (!(holder instanceof GuiMenu.OpenGuiHolder openHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        manager.handleClick(player, openHolder.menuId, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = TrackedInventories.lookup(event.getView().getTopInventory());
        if (holder instanceof GuiMenu.OpenGuiHolder) {
            event.setCancelled(true);
        }
    }
}

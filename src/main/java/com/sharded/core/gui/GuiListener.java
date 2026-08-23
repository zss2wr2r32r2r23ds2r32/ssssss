package com.sharded.core.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.wardrobe.WardrobeModule;
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
        if (event.getView().getTopInventory().getHolder() instanceof WardrobeModule.MenuHolder) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;
            if (event.getClickedInventory() != event.getView().getTopInventory()) return;
            ShardedCore core = ShardedCore.get();
            if (core == null) return;
            if (core.guiSounds() != null) core.guiSounds().play(player, "click");
            WardrobeModule wardrobe = core.modules().get(WardrobeModule.class);
            if (wardrobe != null) wardrobe.handleMenuClick(player, event.getSlot());
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof GuiMenu.OpenGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ShardedCore core = ShardedCore.get();
        if (core != null && core.guiSounds() != null) core.guiSounds().play(player, "click");
        manager.handleClick(player, holder.menuId, event.getSlot());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof WardrobeModule.MenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof GuiMenu.OpenGuiHolder) {
            event.setCancelled(true);
        }
    }
}

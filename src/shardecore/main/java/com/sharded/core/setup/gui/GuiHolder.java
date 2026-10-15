package com.sharded.core.setup.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiHolder implements InventoryHolder {

    private final String id;

    public GuiHolder(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}

package com.shardedmc.lobbycore.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MenuHolder implements InventoryHolder {

    private final MenuType type;
    private Inventory inventory;

    public MenuHolder(MenuType type) {
        this.type = type;
    }

    public MenuType getType() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

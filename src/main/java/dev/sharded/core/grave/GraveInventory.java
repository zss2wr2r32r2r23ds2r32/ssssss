package dev.sharded.core.grave;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GraveInventory implements InventoryHolder {
    private final Grave grave;
    private Inventory inventory;

    public GraveInventory(Grave grave) {
        this.grave = grave;
    }

    public Grave grave() {
        return grave;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

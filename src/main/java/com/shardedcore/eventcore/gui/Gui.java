package com.shardedcore.eventcore.gui;

import com.shardedcore.eventcore.ShardedEventCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Minimal chest-menu base class.
 *
 * <p>Instances are shared between viewers rather than created per player. That
 * keeps a settings menu to a single {@link Inventory} and single click-handler
 * array, and it means a toggle flipped by one admin is visible to any other
 * admin already looking at the same menu.</p>
 *
 * <p>Click routing uses a flat array indexed by raw slot, so dispatch is a bounds
 * check and one array read with no map lookup or autoboxing on the hot path.</p>
 */
public abstract class Gui implements InventoryHolder {

    /** Handler invoked for a click on a specific slot. */
    public interface ClickHandler {
        void handle(Player player, InventoryClickEvent event);
    }

    protected final ShardedEventCore plugin;
    private final int size;
    private Inventory inventory;
    private ClickHandler[] handlers;
    private Component title;

    protected Gui(ShardedEventCore plugin, int rows) {
        this.plugin = plugin;
        this.size = Math.max(9, Math.min(6, rows) * 9);
        this.handlers = new ClickHandler[size];
    }

    /** Title is resolved lazily so config reloads can change it. */
    protected abstract Component title();

    /** Populates items and click handlers. Called on open and on refresh. */
    protected abstract void build();

    @Override
    public final Inventory getInventory() {
        if (inventory == null) {
            title = title();
            inventory = Bukkit.createInventory(this, size, title);
            build();
        }
        return inventory;
    }

    public final int size() {
        return size;
    }

    public void open(Player player) {
        // Inventory titles are immutable, so a renamed menu needs a fresh inventory.
        if (inventory != null && !title().equals(title)) {
            rebuild();
        }
        player.openInventory(getInventory());
    }

    /** Re-runs {@link #build()} in place so every current viewer sees the new state. */
    public void refresh() {
        if (inventory == null) {
            return;
        }
        java.util.Arrays.fill(handlers, null);
        inventory.clear();
        build();
    }

    /** Drops the cached inventory so the next open rebuilds from fresh config. */
    public void invalidate() {
        if (inventory == null) {
            return;
        }
        rebuild();
    }

    private void rebuild() {
        List<HumanEntity> viewers = inventory == null ? List.of() : List.copyOf(inventory.getViewers());
        inventory = null;
        handlers = new ClickHandler[size];
        Inventory rebuilt = getInventory();
        for (HumanEntity viewer : viewers) {
            viewer.openInventory(rebuilt);
        }
    }

    protected final void set(int slot, ItemStack stack, ClickHandler handler) {
        if (slot < 0 || slot >= size) {
            return;
        }
        inventory.setItem(slot, stack);
        handlers[slot] = handler;
    }

    protected final void set(int slot, ItemStack stack) {
        set(slot, stack, null);
    }

    protected final void fill(ItemStack filler) {
        if (filler == null) {
            return;
        }
        for (int slot = 0; slot < size; slot++) {
            if (inventory.getItem(slot) == null) {
                inventory.setItem(slot, filler);
            }
        }
    }

    final void dispatch(Player player, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= handlers.length) {
            return;
        }
        ClickHandler handler = handlers[slot];
        if (handler != null) {
            handler.handle(player, event);
        }
    }

    /** Whether players may take items out of / put items into this menu. */
    public boolean allowsItemMovement() {
        return false;
    }

    public void onClose(Player player) {
    }
}

package com.sharded.core.modules.bundles;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.BundleUtil;
import com.sharded.core.module.Module;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Hides bundle EMPTY/FULL and smithing-template tooltips in plugin menus.
 * Does not affect items players craft or hold normally.
 */
public final class BundlesModule extends Module {

    public BundlesModule(ShardedCore plugin) {
        super(plugin, "bundles");
    }

    @Override
    protected void onEnable() {
    }

    private boolean stripBundles() {
        return config.getBoolean("strip-bundles", true);
    }

    private boolean stripTrimTemplates() {
        return config.getBoolean("strip-trim-templates", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent event) {
        if (!shouldStrip(event.getInventory())) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> stripInventory(event.getInventory()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && shouldStrip(event.getView().getTopInventory())) {
            stripItem(event.getCurrentItem());
        }
        if (event.getCursor() != null && shouldStrip(event.getView().getTopInventory())) {
            stripItem(event.getCursor());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!shouldStrip(event.getView().getTopInventory())) return;
        stripItem(event.getOldCursor());
        stripItem(event.getCursor());
    }

    /** Never strip items while the player is crafting. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCraft(PrepareItemCraftEvent event) {
        // Intentionally empty — crafting results keep normal tooltips.
    }

    private boolean shouldStrip(Inventory inventory) {
        if (inventory == null) return false;
        if (!stripBundles() && !stripTrimTemplates()) return false;
        InventoryType type = inventory.getType();
        if (type == InventoryType.CRAFTING || type == InventoryType.WORKBENCH) return false;
        if (type == InventoryType.PLAYER) return false;
        return type != InventoryType.CREATIVE;
    }

    private void stripInventory(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (stripItem(item)) inventory.setItem(i, item);
        }
    }

    private boolean stripItem(ItemStack item) {
        if (item == null) return false;
        boolean changed = false;
        if (stripBundles()) changed |= BundleUtil.stripBundle(item);
        if (stripTrimTemplates()) changed |= BundleUtil.stripTrimTemplate(item);
        return changed;
    }
}

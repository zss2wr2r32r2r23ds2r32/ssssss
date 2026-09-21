package com.sharded.core.modules.bundles;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.BundleUtil;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class BundlesModule extends Module {
   public BundlesModule(ShardedCore plugin) {
      super(plugin, "bundles");
   }

   @Override
   protected void onEnable() {
   }

   private boolean stripBundles() {
      return this.config.getBoolean("strip-bundles", true);
   }

   private boolean stripTrimTemplates() {
      return this.config.getBoolean("strip-trim-templates", true);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onOpen(InventoryOpenEvent event) {
      if (this.shouldStrip(event.getInventory())) {
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.stripInventory(event.getInventory()));
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getCurrentItem() != null && this.shouldStrip(event.getView().getTopInventory())) {
         this.stripItem(event.getCurrentItem());
      }

      if (event.getCursor() != null && this.shouldStrip(event.getView().getTopInventory())) {
         this.stripItem(event.getCursor());
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onDrag(InventoryDragEvent event) {
      if (this.shouldStrip(event.getView().getTopInventory())) {
         this.stripItem(event.getOldCursor());
         this.stripItem(event.getCursor());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onCraft(PrepareItemCraftEvent event) {
   }

   private boolean shouldStrip(Inventory inventory) {
      if (inventory == null) {
         return false;
      } else if (!this.stripBundles() && !this.stripTrimTemplates()) {
         return false;
      } else {
         InventoryType inventorytype = inventory.getType();
         if (inventorytype == InventoryType.CRAFTING || inventorytype == InventoryType.WORKBENCH) {
            return false;
         } else {
            return inventorytype == InventoryType.PLAYER ? false : inventorytype != InventoryType.CREATIVE;
         }
      }
   }

   private void stripInventory(Inventory inventory) {
      for (int i = 0; i < inventory.getSize(); i++) {
         ItemStack itemstack = inventory.getItem(i);
         if (this.stripItem(itemstack)) {
            inventory.setItem(i, itemstack);
         }
      }
   }

   private boolean stripItem(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         boolean flag = false;
         if (this.stripBundles()) {
            flag |= BundleUtil.stripBundle(item);
         }

         if (this.stripTrimTemplates()) {
            flag |= BundleUtil.stripTrimTemplate(item);
         }

         return flag;
      }
   }
}

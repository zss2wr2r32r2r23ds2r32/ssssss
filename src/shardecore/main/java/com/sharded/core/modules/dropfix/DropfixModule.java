package com.sharded.core.modules.dropfix;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.inventory.ItemStack;

public final class DropfixModule extends Module {
   public DropfixModule(ShardedCore plugin) {
      super(plugin, "dropfix");
   }

   @Override
   protected void onEnable() {
      this.registerListener(this);
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onDrop(EntityDropItemEvent event) {
      if (this.config.getBoolean("merge-stacks", true)) {
         Entity entity = event.getEntity();
         if (!(entity instanceof Player) || this.config.getBoolean("players", true)) {
            if (entity instanceof Player || this.config.getBoolean("mobs", true)) {
               Item item = event.getItemDrop();
               ItemStack itemstack = item.getItemStack();
               if (itemstack != null && itemstack.getType() != Material.AIR) {
                  double d0 = this.config.getDouble("merge-radius", 1.25);
                  Location location = item.getLocation();
                  if (this.config.getBoolean("center-on-block", true)) {
                     Block block = location.getBlock();
                     location = block.getLocation().add(0.5, 0.125, 0.5);
                     item.teleport(location);
                  }

                  for (Entity entity1 : location.getWorld().getNearbyEntities(location, d0, d0, d0, e -> e instanceof Item)) {
                     if (!entity1.getUniqueId().equals(item.getUniqueId())) {
                        Item item1 = (Item)entity1;
                        ItemStack itemstack1 = item1.getItemStack();
                        if (this.similar(itemstack, itemstack1)) {
                           int i = itemstack.getAmount() + itemstack1.getAmount();
                           int j = itemstack.getMaxStackSize();
                           if (i <= j) {
                              itemstack.setAmount(i);
                              item.setItemStack(itemstack);
                              item1.remove();
                           } else {
                              itemstack.setAmount(j);
                              itemstack1.setAmount(i - j);
                              item.setItemStack(itemstack);
                              item1.setItemStack(itemstack1);
                           }

                           return;
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean similar(ItemStack a, ItemStack b) {
      if (a != null && b != null) {
         if (a.getType() != b.getType()) {
            return false;
         } else {
            return a.hasItemMeta() != b.hasItemMeta() ? false : !a.hasItemMeta() || a.getItemMeta().equals(b.getItemMeta());
         }
      } else {
         return false;
      }
   }
}

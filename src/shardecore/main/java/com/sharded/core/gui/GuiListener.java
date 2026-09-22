package com.sharded.core.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.crates.CrateMenu;
import com.sharded.core.modules.tags.TagMenuTitles;
import com.sharded.core.modules.tags.TagsModule;
import com.sharded.core.modules.wardrobe.WardrobeMenuTitles;
import com.sharded.core.modules.wardrobe.WardrobeModule;
import com.sharded.core.util.GuiSounds;
import com.sharded.core.util.TrackedInventories;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

public final class GuiListener implements Listener {
   private final GuiManager manager;

   public GuiListener(GuiManager manager) {
      this.manager = manager;
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onClick(InventoryClickEvent event) {
      Inventory inventory = event.getView().getTopInventory();
      if (!isCrateMenu(inventory)) {
         Object object = resolveHolder(inventory);
         String s = plainTitle(event.getView());
         boolean flag = object instanceof WardrobeModule.MenuHolder || WardrobeMenuTitles.isWardrobeMenu(s);
         if (flag) {
            event.setCancelled(true);
            event.setResult(Result.DENY);
            if (event.getWhoClicked() instanceof Player player1) {
               if (event.getClickedInventory() != inventory) {
                  player1.updateInventory();
               } else {
                  ShardedCore shardedcore = ShardedCore.get();
                  if (shardedcore != null) {
                     GuiSounds guisounds = shardedcore.guiSounds();
                     if (guisounds != null) {
                        guisounds.play(player1, "click");
                     }

                     WardrobeModule wardrobemodule = shardedcore.modules().get(WardrobeModule.class);
                     if (wardrobemodule != null) {
                        wardrobemodule.handleMenuClick(player1, event.getSlot(), event.isRightClick());
                     }

                     player1.updateInventory();
                  }
               }
            }
         } else {
            boolean flag1 = object instanceof TagsModule.Holder || TagMenuTitles.isEquipMenu(s);
            if (flag1) {
               event.setCancelled(true);
               event.setResult(Result.DENY);
               if (event.getWhoClicked() instanceof Player player2) {
                  if (event.getClickedInventory() == inventory) {
                     ShardedCore shardedcore2 = ShardedCore.get();
                     if (shardedcore2 != null) {
                        TagsModule tagsmodule = shardedcore2.modules().get(TagsModule.class);
                        if (tagsmodule != null) {
                           tagsmodule.handleMenuClick(player2, event.getSlot());
                        }
                     }
                  }

                  player2.updateInventory();
               }
            } else if (object instanceof GuiMenu.OpenGuiHolder guimenu$openguiholder) {
               event.setCancelled(true);
               event.setResult(Result.DENY);
               if (event.getWhoClicked() instanceof Player player3) {
                  if (event.getClickedInventory() == inventory) {
                     ShardedCore shardedcore3 = ShardedCore.get();
                     if (shardedcore3 != null && shardedcore3.guiSounds() != null) {
                        shardedcore3.guiSounds().play(player3, "click");
                     }

                     this.manager.handleClick(player3, guimenu$openguiholder.menuId, event.getSlot());
                  }
               }
            } else {
               if (TagMenuTitles.isTokenShop(s) && event.getWhoClicked() instanceof Player player) {
                  event.setCancelled(true);
                  event.setResult(Result.DENY);
                  if (event.getClickedInventory() == inventory) {
                     ShardedCore shardedcore1 = ShardedCore.get();
                     if (shardedcore1 != null && shardedcore1.guiSounds() != null) {
                        shardedcore1.guiSounds().play(player, "click");
                     }

                     this.manager.handleClick(player, "tags", event.getSlot());
                  }

                  player.updateInventory();
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onDrag(InventoryDragEvent event) {
      Inventory inventory = event.getView().getTopInventory();
      if (!isCrateMenu(inventory)) {
         Object object = resolveHolder(inventory);
         String s = plainTitle(event.getView());
         if (object instanceof WardrobeModule.MenuHolder
            || WardrobeMenuTitles.isWardrobeMenu(s)
            || object instanceof TagsModule.Holder
            || object instanceof GuiMenu.OpenGuiHolder
            || TagMenuTitles.isEquipMenu(s)
            || TagMenuTitles.isTokenShop(s)) {
            event.setCancelled(true);
            event.setResult(Result.DENY);
            if (event.getWhoClicked() instanceof Player player) {
               player.updateInventory();
            }
         }
      }
   }

   static String plainTitle(InventoryView view) {
      if (view == null) {
         return "";
      } else {
         try {
            return TagMenuTitles.plain(PlainTextComponentSerializer.plainText().serialize(view.title()));
         } catch (Throwable throwable1) {
            try {
               return TagMenuTitles.plain(view.getTitle());
            } catch (Throwable throwable) {
               return "";
            }
         }
      }
   }

   private static boolean isCrateMenu(Inventory inventory) {
      if (inventory == null) {
         return false;
      } else {
         InventoryHolder inventoryholder = inventory.getHolder();
         if (inventoryholder instanceof CrateMenu) {
            return true;
         } else {
            try {
               Object object = inventory.getClass().getMethod("getHolder", boolean.class).invoke(inventory, false);
               if (object instanceof CrateMenu) {
                  return true;
               }
            } catch (Throwable throwable) {
            }

            return TrackedInventories.lookup(inventory, CrateMenu.class) != null;
         }
      }
   }

   private static Object resolveHolder(Inventory inventory) {
      if (inventory == null) {
         return null;
      } else {
         InventoryHolder inventoryholder = inventory.getHolder();
         if (!(inventoryholder instanceof TagsModule.Holder)
            && !(inventoryholder instanceof WardrobeModule.MenuHolder)
            && !(inventoryholder instanceof GuiMenu.OpenGuiHolder)) {
            try {
               Object object = inventory.getClass().getMethod("getHolder", boolean.class).invoke(inventory, false);
               if (object instanceof TagsModule.Holder || object instanceof WardrobeModule.MenuHolder || object instanceof GuiMenu.OpenGuiHolder) {
                  return object;
               }
            } catch (Throwable throwable) {
            }

            return TrackedInventories.lookup(inventory);
         } else {
            return inventoryholder;
         }
      }
   }
}

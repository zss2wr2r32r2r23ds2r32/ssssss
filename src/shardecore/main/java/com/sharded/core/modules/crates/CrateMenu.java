package com.sharded.core.modules.crates;

import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class CrateMenu implements InventoryHolder {
   private final Inventory inventory;
   private final Map<Integer, Consumer<InventoryClickEvent>> clicks = new HashMap<>();
   private boolean locked = true;
   private Set<Integer> editable;
   private Consumer<InventoryClickEvent> anyClick;
   private Consumer<Player> close;

   public CrateMenu(String title, int rows) {
      this.inventory = Bukkit.createInventory(this, Math.max(1, Math.min(6, rows)) * 9, Text.c(title));
   }

   public Inventory inventory() {
      return this.inventory;
   }

   public CrateMenu editableSlots(Collection<Integer> slots) {
      this.locked = false;
      this.editable = new HashSet<>(slots);
      return this;
   }

   public CrateMenu set(int slot, ItemStack item) {
      if (slot >= 0 && slot < this.inventory.getSize()) {
         this.inventory.setItem(slot, item == null ? null : item.clone());
      }

      return this;
   }

   public CrateMenu set(int slot, ItemStack item, Consumer<InventoryClickEvent> click) {
      this.set(slot, item);
      if (click != null && slot >= 0 && slot < this.inventory.getSize()) {
         this.clicks.put(slot, click);
      }

      return this;
   }

   public CrateMenu onAny(Consumer<InventoryClickEvent> click) {
      this.anyClick = click;
      return this;
   }

   public CrateMenu onClose(Consumer<Player> close) {
      this.close = close;
      return this;
   }

   public void border(ItemStack filler) {
      int i = this.inventory.getSize();
      int j = i / 9;

      for (int k = 0; k < i; k++) {
         int l = k / 9;
         int i1 = k % 9;
         if ((l == 0 || l == j - 1 || i1 == 0 || i1 == 8) && this.inventory.getItem(k) == null) {
            this.inventory.setItem(k, filler);
         }
      }
   }

   public void open(Player player) {
      TrackedInventories.track(this.inventory, this);
      player.openInventory(this.inventory);
   }

   void handleClick(Player player, InventoryClickEvent event) {
      if (this.locked) {
         event.setCancelled(true);
         event.setResult(Result.DENY);
         this.dispatch(event);
      } else {
         int i = event.getRawSlot();
         boolean flag = i >= 0 && i < this.inventory.getSize();
         if (!flag) {
            ClickType clicktype = event.getClick();
            if (clicktype == ClickType.DOUBLE_CLICK) {
               event.setCancelled(true);
            } else if (event.isShiftClick()) {
               event.setCancelled(true);
               this.shiftFromPlayer(event);
            } else {
               event.setCancelled(false);
            }
         } else {
            boolean flag1 = this.editable != null && this.editable.contains(i);
            boolean flag2 = this.clicks.containsKey(i);
            if (!flag1 && !flag2) {
               event.setCancelled(true);
            } else if (flag1 && event.getClick() == ClickType.DOUBLE_CLICK) {
               event.setCancelled(true);
            } else if (flag1) {
               event.setCancelled(false);
            }

            this.dispatch(event);
         }
      }
   }

   void handleDrag(InventoryDragEvent event) {
      if (this.locked) {
         event.setCancelled(true);
         event.setResult(Result.DENY);
      } else if (this.editable != null) {
         int i = this.inventory.getSize();

         for (int j : event.getRawSlots()) {
            if (j < i && (j < 0 || !this.editable.contains(j))) {
               event.setCancelled(true);
               return;
            }
         }

         event.setCancelled(false);
      }
   }

   void handleClose(Player player) {
      try {
         if (this.close != null) {
            this.close.accept(player);
         }
      } finally {
         TrackedInventories.untrack(this.inventory, CrateMenu.class);
      }
   }

   private void dispatch(InventoryClickEvent event) {
      int i = event.getRawSlot();
      if (i >= 0 && i < this.inventory.getSize()) {
         Consumer<InventoryClickEvent> consumer = this.clicks.get(i);
         if (consumer != null) {
            consumer.accept(event);
         }
      }

      if (this.anyClick != null) {
         this.anyClick.accept(event);
      }
   }

   private void shiftFromPlayer(InventoryClickEvent event) {
      if (this.editable != null) {
         Inventory inventory = event.getClickedInventory();
         if (inventory != null) {
            ItemStack itemstack = event.getCurrentItem();
            if (itemstack != null && !itemstack.getType().isAir() && itemstack.getAmount() > 0) {
               ItemStack itemstack1 = itemstack.clone();
               List<Integer> list = new ArrayList<>(this.editable);
               Collections.sort(list);

               for (int i : list) {
                  ItemStack itemstack2 = this.inventory.getItem(i);
                  if (itemstack2 != null && !itemstack2.getType().isAir() && itemstack2.isSimilar(itemstack1)) {
                     int j = itemstack2.getMaxStackSize() - itemstack2.getAmount();
                     if (j > 0) {
                        int k = Math.min(j, itemstack1.getAmount());
                        itemstack2.setAmount(itemstack2.getAmount() + k);
                        itemstack1.setAmount(itemstack1.getAmount() - k);
                        if (itemstack1.getAmount() <= 0) {
                           inventory.setItem(event.getSlot(), null);
                           return;
                        }
                     }
                  }
               }

               for (int l : list) {
                  ItemStack itemstack3 = this.inventory.getItem(l);
                  if (itemstack3 == null || itemstack3.getType().isAir()) {
                     this.inventory.setItem(l, itemstack1);
                     inventory.setItem(event.getSlot(), null);
                     return;
                  }
               }

               inventory.setItem(event.getSlot(), itemstack1);
            }
         }
      }
   }

   public Inventory getInventory() {
      return this.inventory;
   }
}

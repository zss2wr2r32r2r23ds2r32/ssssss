package com.sharded.core.util;

import java.util.Map;
import java.util.WeakHashMap;
import org.bukkit.inventory.Inventory;

public final class TrackedInventories {
   private static final Map<Inventory, Object> HOLDERS = new WeakHashMap<>();

   private TrackedInventories() {
   }

   public static void track(Inventory inventory, Object holder) {
      if (inventory != null && holder != null) {
         HOLDERS.put(inventory, holder);
      }
   }

   public static Object lookup(Inventory inventory) {
      return inventory == null ? null : HOLDERS.get(inventory);
   }

   public static Object untrack(Inventory inventory) {
      return inventory == null ? null : HOLDERS.remove(inventory);
   }

   public static boolean isTracked(Inventory inventory) {
      return inventory != null && HOLDERS.containsKey(inventory);
   }

   public static <T> T lookup(Inventory inventory, Class<T> type) {
      Object object = lookup(inventory);
      return (T)(object != null && type.isInstance(object) ? object : null);
   }

   public static <T> T untrack(Inventory inventory, Class<T> type) {
      Object object = untrack(inventory);
      return (T)(object != null && type.isInstance(object) ? object : null);
   }
}

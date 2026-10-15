package com.sharded.core.util;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class OfflinePlayers {
   private OfflinePlayers() {
   }

   public static OfflinePlayer resolve(String name) {
      if (name != null && !name.isBlank()) {
         OfflinePlayer offlineplayer = Bukkit.getPlayerExact(name);
         if (offlineplayer != null) {
            return offlineplayer;
         } else {
            OfflinePlayer offlineplayer1 = Bukkit.getOfflinePlayerIfCached(name);
            if (offlineplayer1 != null && offlineplayer1.getName() != null) {
               return offlineplayer1;
            } else {
               for (OfflinePlayer offlineplayer2 : Bukkit.getOfflinePlayers()) {
                  if (offlineplayer2.getName() != null && offlineplayer2.getName().equalsIgnoreCase(name)) {
                     return offlineplayer2;
                  }
               }

               return Bukkit.getOfflinePlayer(name);
            }
         }
      } else {
         return null;
      }
   }

   public static String name(UUID uuid) {
      OfflinePlayer offlineplayer = Bukkit.getOfflinePlayer(uuid);
      return offlineplayer.getName() == null ? uuid.toString().substring(0, 8) : offlineplayer.getName();
   }
}

package com.sharded.core.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.BanEntry;
import org.bukkit.Bukkit;
import org.bukkit.BanList.Type;

public final class VanillaBanHelper {
   private VanillaBanHelper() {
   }

   public static void pardonIp(String ip) {
      if (ip != null && !ip.isBlank()) {
         Bukkit.getBanList(Type.IP).pardon(ip);
      }
   }

   public static void pardonName(String name) {
      if (name != null && !name.isBlank()) {
         Bukkit.getBanList(Type.NAME).pardon(name);

         try {
            Bukkit.getBanList(Type.PROFILE).pardon(name);
         } catch (Exception exception) {
         }
      }
   }

   public static boolean isIpBanned(String ip) {
      return ip != null && !ip.isBlank() ? Bukkit.getBanList(Type.IP).isBanned(ip) : false;
   }

   public static boolean isNameBanned(String name) {
      if (name != null && !name.isBlank()) {
         if (Bukkit.getBanList(Type.NAME).isBanned(name)) {
            return true;
         } else {
            try {
               return Bukkit.getBanList(Type.PROFILE).isBanned(name);
            } catch (Exception exception) {
               return false;
            }
         }
      } else {
         return false;
      }
   }

   public static List<String> vanillaIpBans() {
      List<String> list = new ArrayList<>();

      for (BanEntry<?> banentry : Bukkit.getBanList(Type.IP).getBanEntries()) {
         String s = banentry.getTarget();
         if (s != null && !s.isBlank()) {
            list.add(s);
         }
      }

      return list;
   }

   public static List<String> vanillaNameBans() {
      List<String> list = new ArrayList<>();

      for (BanEntry<?> banentry : Bukkit.getBanList(Type.NAME).getBanEntries()) {
         String s = banentry.getTarget();
         if (s != null && !s.isBlank()) {
            list.add(s);
         }
      }

      return list;
   }
}

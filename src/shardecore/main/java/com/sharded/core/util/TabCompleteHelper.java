package com.sharded.core.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class TabCompleteHelper {
   private TabCompleteHelper() {
   }

   public static List<String> filter(String input, String... options) {
      return filter(input, Arrays.asList(options));
   }

   public static List<String> filter(String input, Collection<String> options) {
      String s = input == null ? "" : input.toLowerCase(Locale.ROOT);
      List<String> list = new ArrayList<>();

      for (String s1 : options) {
         if (s1.toLowerCase(Locale.ROOT).startsWith(s)) {
            list.add(s1);
         }
      }

      return list;
   }

   public static List<String> onlinePlayers(String input) {
      return onlinePlayers(input, false);
   }

   public static List<String> onlinePlayers(String input, boolean offlineAdmins) {
      List<String> list = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         list.add(player.getName());
      }

      if (offlineAdmins) {
         for (OfflinePlayer offlineplayer : Bukkit.getOfflinePlayers()) {
            if ((offlineplayer.getName() == null || !offlineplayer.isOnline()) && offlineplayer.getName() != null) {
               list.add(offlineplayer.getName());
            }
         }
      }

      return filter(input, list);
   }

   public static List<String> ifPermission(CommandSender sender, String permission, String input, String... options) {
      return !sender.hasPermission(permission) ? List.of() : filter(input, options);
   }

   public static List<String> knownPlayers(String input) {
      List<String> list = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         list.add(player.getName());
      }

      for (OfflinePlayer offlineplayer : Bukkit.getOfflinePlayers()) {
         if (offlineplayer.getName() != null && !offlineplayer.isOnline()) {
            list.add(offlineplayer.getName());
         }
      }

      return filter(input, list);
   }

   public static List<String> configKeys(String input, Collection<String> keys) {
      return filter(input, keys);
   }
}

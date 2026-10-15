package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.tokens.TokenService;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class EventRewards {
   private EventRewards() {
   }

   public static void grant(ShardedCore plugin, UUID uuid, ConfigurationSection section) {
      if (section != null) {
         long i = section.getLong("tokens", 0L);
         TokenService tokenservice = plugin.modules().tokens();
         if (tokenservice != null && i > 0L) {
            tokenservice.give(uuid, i);
         }

         Player player = Bukkit.getPlayer(uuid);
         String s = player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();
         if (s == null) {
            s = uuid.toString();
         }

         for (String s1 : section.getStringList("commands")) {
            if (s1 != null && !s1.isBlank()) {
               String s2 = s1.replace("%player%", s).replace("%player_name%", s).replace("%uuid%", uuid.toString());
               if (s2.startsWith("[console]")) {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s2.substring("[console]".length()).trim());
               } else if (player != null) {
                  player.performCommand(s2.startsWith("/") ? s2.substring(1) : s2);
               } else {
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s2.startsWith("/") ? s2.substring(1) : s2);
               }
            }
         }
      }
   }
}

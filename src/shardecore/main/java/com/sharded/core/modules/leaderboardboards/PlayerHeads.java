package com.sharded.core.modules.leaderboardboards;

import com.destroystokyo.paper.profile.PlayerProfile;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

final class PlayerHeads {
   private PlayerHeads() {
   }

   static ItemStack of(UUID uuid, String name) {
      ItemStack itemstack = new ItemStack(Material.PLAYER_HEAD);
      if (!(itemstack.getItemMeta() instanceof SkullMeta skullmeta)) {
         return itemstack;
      } else {
         String s = name != null && !name.isBlank() ? name : "Unknown";
         if (uuid != null) {
            PlayerProfile playerprofile = Bukkit.createProfile(uuid, s);
            skullmeta.setPlayerProfile(playerprofile);
         } else if (!s.equals("Unknown")) {
            skullmeta.setOwningPlayer(Bukkit.getOfflinePlayer(s));
         }

         itemstack.setItemMeta(skullmeta);
         return itemstack;
      }
   }

   static ItemStack empty() {
      return new ItemStack(Material.AIR);
   }
}

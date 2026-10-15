package com.sharded.core.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public final class HeadUtil {
   private HeadUtil() {
   }

   public static ItemStack parse(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim();
         if (s.equalsIgnoreCase("PLAYER_HEAD") || s.equalsIgnoreCase("%player_head%")) {
            return new ItemStack(Material.PLAYER_HEAD);
         } else if (s.regionMatches(true, 0, "basehead-", 0, 9)) {
            return textureHead(s.substring(9).trim());
         } else if (s.regionMatches(true, 0, "head:", 0, 5)) {
            return namedHead(s.substring(5).trim());
         } else if (s.regionMatches(true, 0, "texture:", 0, 8)) {
            return textureHead(s.substring(8).trim());
         } else {
            return s.startsWith("eyJ") ? textureHead(s) : null;
         }
      } else {
         return null;
      }
   }

   public static ItemStack applyViewer(ItemStack stack, Player viewer) {
      if (stack != null && viewer != null && stack.getType() == Material.PLAYER_HEAD) {
         ItemStack itemstack = stack.clone();
         SkullMeta skullmeta = (SkullMeta)itemstack.getItemMeta();
         if (skullmeta == null) {
            return itemstack;
         } else {
            skullmeta.setOwningPlayer(viewer);
            itemstack.setItemMeta(skullmeta);
            return itemstack;
         }
      } else {
         return stack;
      }
   }

   public static ItemStack namedHead(String playerName) {
      ItemStack itemstack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta skullmeta = (SkullMeta)itemstack.getItemMeta();
      if (skullmeta != null) {
         skullmeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
         itemstack.setItemMeta(skullmeta);
      }

      return itemstack;
   }

   public static ItemStack textureHead(String base64) {
      ItemStack itemstack = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta skullmeta = (SkullMeta)itemstack.getItemMeta();
      if (skullmeta == null) {
         return itemstack;
      } else {
         UUID uuid = UUID.nameUUIDFromBytes(("sharded-head:" + base64).getBytes(StandardCharsets.UTF_8));
         PlayerProfile playerprofile = Bukkit.createProfile(uuid, "head");
         playerprofile.setProperty(new ProfileProperty("textures", base64));
         skullmeta.setPlayerProfile(playerprofile);
         itemstack.setItemMeta(skullmeta);
         return itemstack;
      }
   }

   public static boolean isViewerHeadMaterial(String raw) {
      if (raw == null) {
         return false;
      } else {
         String s = raw.trim().toLowerCase(Locale.ROOT);
         return s.equals("player_head") || s.equals("%player_head%");
      }
   }

   public static boolean isHeadMaterial(String raw) {
      if (raw == null) {
         return false;
      } else {
         String s = raw.toLowerCase(Locale.ROOT);
         return s.equals("player_head")
            || s.equals("%player_head%")
            || s.startsWith("basehead-")
            || s.startsWith("head:")
            || s.startsWith("texture:")
            || raw.startsWith("eyJ");
      }
   }
}

package com.sharded.core.cosmetics;

import com.sharded.core.ShardedCore;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CosmeticsPlaceholders {
   private CosmeticsPlaceholders() {
   }

   public static void register(ShardedCore plugin, CosmeticService cosmetics) {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         try {
            new CosmeticsPlaceholders.Expansion(plugin, cosmetics).register();
         } catch (Throwable throwable) {
            plugin.getLogger().warning("[cosmetics] Could not register name placeholders: " + throwable.getMessage());
         }
      }
   }

   private static final class Expansion extends PlaceholderExpansion {
      private final ShardedCore plugin;
      private final CosmeticService cosmetics;

      private Expansion(ShardedCore plugin, CosmeticService cosmetics) {
         this.plugin = plugin;
         this.cosmetics = cosmetics;
      }

      @NotNull
      public String getIdentifier() {
         return "shardedname";
      }

      @NotNull
      public String getAuthor() {
         return "Sharded";
      }

      @NotNull
      public String getVersion() {
         return this.plugin.getDescription().getVersion();
      }

      public boolean persist() {
         return true;
      }

      @Nullable
      public String onPlaceholderRequest(Player player, @NotNull String params) {
         if (player != null && this.cosmetics != null) {
            String s = params.toLowerCase();

            return switch (s) {
               case "colored", "name", "colored_name", "gradient" -> CosmeticService.toHashHexFormat(this.cosmetics.formattedName(player));
               case "tab", "tab_name", "list", "list_name" -> CosmeticService.toHashHexFormat(this.cosmetics.tabNameLegacy(player));
               case "colored_legacy", "name_legacy" -> this.cosmetics.formattedName(player);
               case "tab_legacy" -> this.cosmetics.tabNameLegacy(player);
               default -> null;
            };
         } else {
            return "";
         }
      }
   }
}

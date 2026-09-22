package com.sharded.core.modules.itemshop;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class ItemShopTypes {
   private ItemShopTypes() {
   }

   public record Rarity(
      String id,
      String displayName,
      String color,
      String gradient,
      int weight,
      long defaultPriceHat,
      long defaultPriceTag,
      Material borderMaterial,
      String loreLine,
      String purchaseSound,
      float purchaseVolume,
      float purchasePitch,
      boolean broadcastOnPurchase,
      String broadcastMessage,
      boolean glow,
      int sortOrder
   ) {
      long defaultPrice(String type) {
         return "tag".equals(type) ? this.defaultPriceTag : this.defaultPriceHat;
      }
   }

   public record Cosmetic(
      String type,
      String id,
      String displayName,
      List<String> lore,
      String rarityId,
      Long price,
      boolean enabled,
      boolean inRotation,
      boolean featured,
      String permission,
      List<String> servers,
      String material,
      String itemModel,
      String headTexture,
      double offsetX,
      double offsetY,
      double offsetZ,
      float scale,
      float rotX,
      float rotY,
      float rotZ,
      String tagText,
      String position
   ) {
      boolean allowedOn(String serverId) {
         return this.servers == null || this.servers.isEmpty() || this.servers.stream().anyMatch(s -> s.equalsIgnoreCase(serverId));
      }
   }

   public record Listing(String type, String id, int slotIndex) {
   }

   static Material material(ConfigurationSection section, String path, Material fallback) {
      if (section == null) {
         return fallback;
      }
      String raw = section.getString(path, fallback.name());
      Material material = Material.matchMaterial(raw == null ? "" : raw);
      return material == null || material.isAir() ? fallback : material;
   }
}

package com.sharded.core.modules.pets;

import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Axolotl.Variant;

public enum PetType {
   PARROT("parrot", EntityType.PARROT, 0.55, false, false, false, true, null, null),
   AXOLOTL("axolotl", EntityType.AXOLOTL, 0.45, false, false, false, false, null, null),
   BEE("bee", EntityType.BEE, 0.45, false, false, false, true, null, null),
   BAT("bat", EntityType.BAT, 0.35, false, false, false, true, null, null),
   ALLAY("allay", EntityType.ALLAY, 0.65, false, false, false, true, null, null);

   private final String id;
   private final EntityType entityType;
   private final double scale;
   private final boolean groundSnap;
   private final boolean armorStand;
   private final boolean flyOrbit;
   private final Material helmet;
   private final String headTexture;

   private PetType(
      String id,
      EntityType entityType,
      double scale,
      boolean shoulder,
      boolean groundSnap,
      boolean armorStand,
      boolean flyOrbit,
      Material helmet,
      String headTexture
   ) {
      this.id = id;
      this.entityType = entityType;
      this.scale = scale;
      this.groundSnap = groundSnap;
      this.armorStand = armorStand;
      this.flyOrbit = flyOrbit;
      this.helmet = helmet;
      this.headTexture = headTexture;
   }

   public String id() {
      return this.id;
   }

   public EntityType entityType() {
      return this.entityType;
   }

   public double scale() {
      return this.scale;
   }

   public boolean groundSnap() {
      return this.groundSnap;
   }

   public boolean armorStand() {
      return this.armorStand;
   }

   public boolean flyOrbit() {
      return this.flyOrbit;
   }

   public Material helmet() {
      return this.helmet;
   }

   public String headTexture() {
      return this.headTexture;
   }

   public boolean supportsVariant() {
      return this == AXOLOTL;
   }

   public String permission() {
      return "sharded.pets." + this.id;
   }

   public static PetType fromId(String raw) {
      if (raw == null) {
         return null;
      } else {
         String s = raw.toLowerCase(Locale.ROOT);

         for (PetType pettype : values()) {
            if (pettype.id.equals(s) || pettype.name().equalsIgnoreCase(s)) {
               return pettype;
            }
         }

         if (s.equals("dragon") || s.equals("enderdragon")) {
            return ALLAY;
         } else {
            return s.equals("happy_ghast") ? BAT : null;
         }
      }
   }

   public static Variant parseAxolotlVariant(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Variant.valueOf(raw.toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException illegalargumentexception) {
            String s = raw.toLowerCase(Locale.ROOT);

            return switch (s) {
               case "pink" -> Variant.LUCY;
               case "brown" -> Variant.WILD;
               case "gold" -> Variant.GOLD;
               case "cyan", "teal" -> Variant.CYAN;
               case "blue" -> Variant.BLUE;
               default -> Variant.LUCY;
            };
         }
      } else {
         return Variant.LUCY;
      }
   }

   public static List<String> axolotlColorNames() {
      return List.of("lucy", "wild", "gold", "cyan", "blue", "pink", "brown", "teal");
   }

   public static boolean isValidAxolotlColor(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.toLowerCase(Locale.ROOT);
         return axolotlColorNames().contains(s);
      } else {
         return true;
      }
   }
}

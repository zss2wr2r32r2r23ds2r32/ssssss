package com.sharded.core.modules.protect;

import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;

final class SideBlockedMaterials {
   private static final Set<Material> BLOCKED = build();

   private SideBlockedMaterials() {
   }

   static boolean isBlocked(Material type) {
      return BLOCKED.contains(type);
   }

   private static Set<Material> build() {
      Set<Material> set = new HashSet<>();
      set.add(Material.ANVIL);
      set.add(Material.CHIPPED_ANVIL);
      set.add(Material.DAMAGED_ANVIL);
      set.add(Material.BEACON);
      set.add(Material.OAK_TRAPDOOR);
      set.add(Material.SPRUCE_TRAPDOOR);
      set.add(Material.BIRCH_TRAPDOOR);
      set.add(Material.JUNGLE_TRAPDOOR);
      set.add(Material.ACACIA_TRAPDOOR);
      set.add(Material.DARK_OAK_TRAPDOOR);
      set.add(Material.MANGROVE_TRAPDOOR);
      set.add(Material.CHERRY_TRAPDOOR);
      set.add(Material.BAMBOO_TRAPDOOR);
      set.add(Material.CRIMSON_TRAPDOOR);
      set.add(Material.WARPED_TRAPDOOR);
      set.add(Material.IRON_TRAPDOOR);

      for (Material material : Material.values()) {
         if (material.isInteractable()) {
            String s = material.name();
            if (s.endsWith("_TRAPDOOR")
               || s.endsWith("_DOOR")
               || s.endsWith("_FENCE_GATE")
               || s.endsWith("_BUTTON")
               || s.equals("LEVER")
               || s.endsWith("_CHEST")
               || s.equals("BARREL")
               || s.endsWith("FURNACE")
               || s.equals("HOPPER")
               || s.equals("DROPPER")
               || s.equals("DISPENSER")
               || s.equals("CRAFTING_TABLE")
               || s.equals("ENCHANTING_TABLE")) {
               set.add(material);
            }
         }
      }

      return Set.copyOf(set);
   }
}

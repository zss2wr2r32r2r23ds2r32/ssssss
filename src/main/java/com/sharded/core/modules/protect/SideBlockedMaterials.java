package com.sharded.core.modules.protect;

import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

/** Precomputed interactable materials blocked in side regions. */
final class SideBlockedMaterials {

    private static final Set<Material> BLOCKED = build();

    private SideBlockedMaterials() {
    }

    static boolean isBlocked(Material type) {
        return BLOCKED.contains(type);
    }

    private static Set<Material> build() {
        Set<Material> blocked = new HashSet<>();
        blocked.add(Material.ANVIL);
        blocked.add(Material.CHIPPED_ANVIL);
        blocked.add(Material.DAMAGED_ANVIL);
        blocked.add(Material.BEACON);
        blocked.add(Material.OAK_TRAPDOOR);
        blocked.add(Material.SPRUCE_TRAPDOOR);
        blocked.add(Material.BIRCH_TRAPDOOR);
        blocked.add(Material.JUNGLE_TRAPDOOR);
        blocked.add(Material.ACACIA_TRAPDOOR);
        blocked.add(Material.DARK_OAK_TRAPDOOR);
        blocked.add(Material.MANGROVE_TRAPDOOR);
        blocked.add(Material.CHERRY_TRAPDOOR);
        blocked.add(Material.BAMBOO_TRAPDOOR);
        blocked.add(Material.CRIMSON_TRAPDOOR);
        blocked.add(Material.WARPED_TRAPDOOR);
        blocked.add(Material.IRON_TRAPDOOR);
        for (Material material : Material.values()) {
            if (!material.isInteractable()) continue;
            String name = material.name();
            if (name.endsWith("_TRAPDOOR") || name.endsWith("_DOOR")
                    || name.endsWith("_FENCE_GATE") || name.endsWith("_BUTTON")
                    || name.equals("LEVER") || name.endsWith("_CHEST") || name.equals("BARREL")
                    || name.endsWith("FURNACE") || name.equals("HOPPER") || name.equals("DROPPER")
                    || name.equals("DISPENSER") || name.equals("CRAFTING_TABLE")
                    || name.equals("ENCHANTING_TABLE")) {
                blocked.add(material);
            }
        }
        return Set.copyOf(blocked);
    }
}

package com.sharded.core.modules.crates;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateShulkersTest {
    @Test
    void browseIconsAreShulkersNeverArmorOrChests() {
        for (String id : new String[]{"vote", "shard", "keyall", "legendary", "common", "a"}) {
            Material icon = CrateShulkers.forId(id);
            assertTrue(CrateShulkers.isShulker(icon), id);
            assertFalse(icon.name().contains("CHEST"), id);
            assertFalse(icon.name().contains("HELMET"), id);
            assertFalse(icon.name().contains("BOOTS"), id);
        }
        assertTrue(CrateShulkers.pretty(Material.PURPLE_SHULKER_BOX).contains("Purple"));
        assertTrue(CrateShulkers.isShulker(CrateShulkers.next(Material.RED_SHULKER_BOX)));
    }
}

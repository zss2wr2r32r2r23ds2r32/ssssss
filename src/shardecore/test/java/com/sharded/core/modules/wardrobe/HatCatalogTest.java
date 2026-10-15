package com.sharded.core.modules.wardrobe;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HatCatalogTest {
    @Test
    void catalogHasUniqueIdsAndBothPacks() {
        Set<String> ids = new HashSet<>();
        Set<String> ia = new HashSet<>();
        for (HatCatalog.Seed seed : HatCatalog.all()) {
            assertTrue(ids.add(seed.id()), "duplicate hat id " + seed.id());
            assertTrue(ia.add(seed.itemsadderId()), "duplicate itemsadder id " + seed.itemsadderId());
        }
        assertTrue(ids.contains("tophat"));
        assertTrue(ids.contains("dimmadome"));
        assertTrue(ids.contains("top_hat"));
        assertTrue(ids.contains("somehats_crown"));
        assertTrue(ia.contains("hats:tophat"));
        assertTrue(ia.contains("somehats:top_hat"));
        assertEquals(102, ids.size());
    }

    @Test
    void hatsGetDistinctAccentColors() {
        assertTrue(HatCatalog.colorFor("tophat").startsWith("&#"));
        assertTrue(!HatCatalog.colorFor("tophat").equals(HatCatalog.colorFor("dimmadome"))
                || !HatCatalog.colorFor("crown").equals(HatCatalog.colorFor("santa_hat")));
    }
}

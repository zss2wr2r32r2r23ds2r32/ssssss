package com.sharded.core.modules.tags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TagMenuTitlesTest {
    @Test
    void equipTitlesAreNotTheTokenShop() {
        assertTrue(TagMenuTitles.isEquipMenu("Name Tags"));
        assertTrue(TagMenuTitles.isEquipMenu("§fName Tags"));
        assertTrue(TagMenuTitles.isEquipMenu("&lLimited Tags"));
        assertFalse(TagMenuTitles.isEquipMenu("Tags"));
        assertFalse(TagMenuTitles.isEquipMenu("Wardrobe"));
    }

    @Test
    void tokenShopTitleIsExactTags() {
        assertTrue(TagMenuTitles.isTokenShop("Tags"));
        assertTrue(TagMenuTitles.isTokenShop("§8Tags"));
        assertFalse(TagMenuTitles.isTokenShop("Name Tags"));
        assertFalse(TagMenuTitles.isTokenShop("Limited Tags"));
    }
}

package com.sharded.core.modules.wardrobe;

import com.sharded.core.modules.tags.TagMenuTitles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WardrobeMenuTitlesTest {
    @Test
    void wardrobeTitlesAreHatsNotTags() {
        assertTrue(WardrobeMenuTitles.isWardrobeMenu("Wardrobe"));
        assertTrue(WardrobeMenuTitles.isWardrobeMenu("§fWardrobe"));
        assertTrue(WardrobeMenuTitles.isWardrobeMenu("Wardrobe | Limited"));
        assertTrue(WardrobeMenuTitles.isWardrobeMenu("Wardrobe | Favourites"));
        assertTrue(WardrobeMenuTitles.isWardrobeMenu("Token Shop | Cosmetics"));
        assertFalse(WardrobeMenuTitles.isWardrobeMenu("Name Tags"));
        assertFalse(WardrobeMenuTitles.isWardrobeMenu("Limited Tags"));
        assertFalse(WardrobeMenuTitles.isWardrobeMenu("Tags"));
    }

    @Test
    void tagsEquipTitlesStayOnTheTagsMenu() {
        assertTrue(TagMenuTitles.isEquipMenu("Name Tags"));
        assertFalse(TagMenuTitles.isEquipMenu("Wardrobe"));
        assertFalse(TagMenuTitles.isEquipMenu("Token Shop | Cosmetics"));
    }
}

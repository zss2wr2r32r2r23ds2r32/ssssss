package com.sharded.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsAdderHookTest {
    @Test
    void tophatCandidatesCoverCommonItemsAdderIds() {
        List<String> ids = ItemsAdderHook.candidateIds("HATS:TOPHAT");
        assertTrue(ids.contains("HATS:TOPHAT"));
        assertTrue(ids.contains("hats:tophat"));
        assertTrue(ids.contains("hats:top_hat") || ids.contains("HATS:TOP_HAT"));
        assertTrue(ids.stream().anyMatch(id -> id.equalsIgnoreCase("tophat")));
        assertTrue(ids.stream().anyMatch(id -> id.equalsIgnoreCase("top_hat")));
    }

    @Test
    void strawHatAliasesIncludeTypoAndCorrectId() {
        List<String> aliases = ItemsAdderHook.hatAliases("sstraw_hat");
        assertTrue(aliases.contains("straw_hat"));
        assertTrue(aliases.contains("sstraw_hat"));
    }

    @Test
    void normalizeStripsSeparators() {
        assertTrue(ItemsAdderHook.normalize("TOP Hat").equals("tophat"));
        assertTrue(ItemsAdderHook.normalize("hats:top_hat").equals("hats:tophat")
                || ItemsAdderHook.normalize("top_hat").equals("tophat"));
        assertTrue(ItemsAdderHook.normalize("top_hat").equals("tophat"));
    }

    @Test
    void vanillaMaterialNamesAreNotTreatedAsNamespaced() {
        List<String> ids = ItemsAdderHook.candidateIds("PINK_WOOL");
        assertFalse(ids.stream().anyMatch(id -> id.contains("hats:PINK")));
    }
}

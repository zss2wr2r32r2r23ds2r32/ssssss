package com.sharded.core.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemsAdderEscMenuSyncTest {
    @Test
    void langUsesLegacyCodesThatSurviveUppercase() {
        String lang = ItemsAdderEscMenuSync.toMinecraftLang(ItemsAdderEscMenuSync.DEFAULT_TEXT);
        assertEquals("\u00A7dDisconnect \u00A7fFrom \u00A7dShardedMC", lang);
        assertFalse(lang.contains("AD4EFF"));
        assertFalse(lang.contains("&#AD4EFF"));
        assertFalse(lang.contains("\u00A7x"));
        String upper = lang.toUpperCase(Locale.ROOT);
        assertFalse(upper.contains("AD4EFF"));
        assertFalse(ItemsAdderEscMenuSync.breaksWhenUppercased(lang));
        assertTrue(upper.contains("DISCONNECT"));
        assertTrue(upper.contains("FROM"));
        assertTrue(upper.contains("SHARDEDMC"));
    }

    @Test
    void hexDisconnectIsLegacyAndWouldPrintAd4eff() {
        assertTrue(ItemsAdderEscMenuSync.isLegacyDisconnect(
                "&x&A&D&4&E&F&FDisconnect from ShardedMC"));
        assertTrue(ItemsAdderEscMenuSync.isLegacyDisconnect(
                "&#AD4EFFDisconnect &fFrom &#AD4EFFShardedMC"));
        assertTrue(ItemsAdderEscMenuSync.breaksWhenUppercased(
                ItemsAdderEscMenuSync.toMinecraftLang("&#AD4EFFDisconnect &fFrom &#AD4EFFShardedMC")));
        assertFalse(ItemsAdderEscMenuSync.isLegacyDisconnect(ItemsAdderEscMenuSync.DEFAULT_TEXT));
    }
}

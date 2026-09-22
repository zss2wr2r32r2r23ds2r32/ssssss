package com.sharded.core.modules.guide;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GuideLayoutTest {
    @Test
    void guideUsesFourRowTokenShopLayout() throws IOException {
        String yaml = Files.readString(
                Path.of("overlay/resources/modules/guide/guide.yml"),
                StandardCharsets.UTF_8);
        assertTrue(yaml.contains("config-version: 11"));
        assertTrue(yaml.contains("size: 36"));
        assertTrue(yaml.contains("menu_title: 'Guide'"));
        assertTrue(yaml.contains("BLACK_STAINED_GLASS_PANE"));
        assertTrue(yaml.contains("  teams:"));
        assertTrue(yaml.contains("slot: 11"));
        assertTrue(yaml.contains("material: PINK_BANNER"));
        assertTrue(yaml.contains("  tokenshop:"));
        assertTrue(yaml.contains("material: AMETHYST_SHARD"));
        assertTrue(yaml.contains("[player] tokenshop"));
        assertTrue(yaml.contains("  random-teleport:"));
        assertTrue(yaml.contains("material: MOSS_BLOCK"));
        assertTrue(yaml.contains("  leaderboards:"));
        assertTrue(yaml.contains("material: GUSTER_BANNER_PATTERN"));
        assertTrue(yaml.contains("  webstore:"));
        assertTrue(yaml.contains("material: LIME_HARNESS"));
        assertTrue(yaml.contains("  rules:"));
        assertTrue(yaml.contains("material: PINK_BUNDLE"));
        assertTrue(yaml.contains("  discord:"));
        assertTrue(yaml.contains("material: LIGHT_BLUE_SHULKER_BOX"));
        assertFalse(yaml.contains("crystal-shop"));
        assertFalse(yaml.contains("crystalshop"));
        assertFalse(yaml.contains("  server-shop:"));
        assertFalse(yaml.contains("SPYGLASS"));
        assertFalse(yaml.contains("RECOVERY_COMPASS"));
    }
}

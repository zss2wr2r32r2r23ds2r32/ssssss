package com.sharded.core.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemsAdderLocatorPackTest {
    @TempDir
    File temp;

    @Test
    void writesMinecraftAndShardedSpritesIntoContents() throws Exception {
        File itemsAdder = new File(this.temp, "ItemsAdder");
        Map<String, byte[]> pack = samplePack();
        assertTrue(ItemsAdderLocatorPack.sync(itemsAdder, path -> open(pack, path)));

        File root = ItemsAdderLocatorPack.contentRoot(itemsAdder);
        assertTrue(new File(root, "configs/locator.yml").isFile());
        assertTrue(new File(root, "resourcepack/assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_o.png").isFile());
        assertTrue(new File(root, "resourcepack/assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_k.png").isFile());
        assertEquals(
                "o-icon",
                Files.readString(new File(root,
                        "resourcepack/assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_o.png").toPath()));
        assertEquals(
                "k-icon",
                Files.readString(new File(root,
                        "resourcepack/assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_k.png").toPath()));
        assertTrue(Files.readString(new File(root, "configs/locator.yml").toPath()).contains("sharded_locator"));
    }

    @Test
    void secondSyncIsUnchanged() throws Exception {
        File itemsAdder = new File(this.temp, "ItemsAdder");
        Map<String, byte[]> pack = samplePack();
        assertTrue(ItemsAdderLocatorPack.sync(itemsAdder, path -> open(pack, path)));
        assertFalse(ItemsAdderLocatorPack.sync(itemsAdder, path -> open(pack, path)));
    }

    @Test
    void updatedIconRewritesAndMarksChanged() throws Exception {
        File itemsAdder = new File(this.temp, "ItemsAdder");
        Map<String, byte[]> pack = samplePack();
        assertTrue(ItemsAdderLocatorPack.sync(itemsAdder, path -> open(pack, path)));
        pack.put("locator-bar-pack/assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_o.png",
                "o-icon-v2".getBytes(StandardCharsets.UTF_8));
        assertTrue(ItemsAdderLocatorPack.sync(itemsAdder, path -> open(pack, path)));
        File icon = new File(ItemsAdderLocatorPack.contentRoot(itemsAdder),
                "resourcepack/assets/minecraft/textures/gui/sprites/hud/locator_bar_dot/event_o.png");
        assertEquals("o-icon-v2", Files.readString(icon.toPath()));
    }

    @Test
    void copiesIntoLegacyItemsPacksWhenThatFolderExists() throws Exception {
        File itemsAdder = new File(this.temp, "ItemsAdder");
        assertTrue(new File(itemsAdder, "data/items_packs").mkdirs());
        Map<String, byte[]> pack = samplePack();
        assertTrue(ItemsAdderLocatorPack.sync(itemsAdder, path -> open(pack, path)));
        File legacy = new File(ItemsAdderLocatorPack.itemsPacksRoot(itemsAdder),
                "resourcepack/assets/minecraft/waypoint_style/event_k.json");
        assertTrue(legacy.isFile());
        assertEquals("k-style", Files.readString(legacy.toPath()));
    }

    private static Map<String, byte[]> samplePack() {
        Map<String, byte[]> pack = new HashMap<>();
        for (String relative : ItemsAdderLocatorPack.ASSET_FILES) {
            String marker = relative.contains("event_k") ? "k" : relative.contains("event_o") ? "o" : "f";
            String body = relative.endsWith(".json") ? marker + "-style" : marker + "-icon";
            pack.put("locator-bar-pack/" + relative, body.getBytes(StandardCharsets.UTF_8));
        }
        return pack;
    }

    private static ByteArrayInputStream open(Map<String, byte[]> pack, String path) {
        byte[] bytes = pack.get(path);
        return bytes == null ? null : new ByteArrayInputStream(bytes);
    }
}

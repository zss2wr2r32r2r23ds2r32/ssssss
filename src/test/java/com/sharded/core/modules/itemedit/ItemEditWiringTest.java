package com.sharded.core.modules.itemedit;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ItemEditWiringTest {
    @Test
    void pluginYmlDeclaresItemEditAndGravesNotHide() throws IOException {
        String yaml = Files.readString(Path.of("overlay/plugin.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("  itemedit:"));
        assertTrue(yaml.contains("  serveritem:"));
        assertTrue(yaml.contains("  itemstorage:"));
        assertTrue(yaml.contains("aliases: [ie]"));
        assertTrue(yaml.contains("aliases: [si]"));
        assertTrue(yaml.contains("aliases: [is]"));
        assertTrue(yaml.contains("  graves:"));
        assertTrue(yaml.contains("sharded.itemedit.use:"));
        assertTrue(yaml.contains("sharded.graves.use:"));
        assertFalse(yaml.contains("  hide:"));
        assertFalse(yaml.contains("  whois:"));
        assertFalse(yaml.contains("sharded.hide"));
        assertTrue(yaml.contains("version: '1.6.91'"));
    }

    @Test
    void ranksLoreHasGravesNotHideCommand() throws IOException {
        String yaml = Files.readString(
                Path.of("overlay/resources/modules/ranksinfo/config.yml"),
                StandardCharsets.UTF_8);
        assertTrue(yaml.contains("Graves — loot goes into chests on kills"));
        assertFalse(yaml.contains("/hide"));
        assertTrue(yaml.contains("config-version: 12"));
    }

    @Test
    void mainConfigEnablesGravesAndItemEdit() throws IOException {
        String yaml = Files.readString(Path.of("overlay/resources/config.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("graves: true"));
        assertTrue(yaml.contains("itemedit: true"));
        assertFalse(yaml.lines()
                .map(String::trim)
                .anyMatch(line -> line.equals("hide: true") || line.equals("hide: false")));
    }
}

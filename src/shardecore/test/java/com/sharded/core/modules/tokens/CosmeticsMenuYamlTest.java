package com.sharded.core.modules.tokens;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CosmeticsMenuYamlTest {
    @Test
    void tokenShopCosmeticsMenuDoesNotStealHatshop() throws IOException {
        String yaml = Files.readString(
                Path.of("overlay/resources/modules/tokens/tokens/menus/cosmetics.yml"),
                StandardCharsets.UTF_8);
        assertFalse(yaml.lines()
                .map(String::trim)
                .filter(line -> !line.startsWith("#"))
                .anyMatch(line -> line.startsWith("open_command:")));
        assertTrue(yaml.contains("menu_title: 'Token Shop | Cosmetics'"));
        assertTrue(yaml.contains("config-version: 9"));
    }
}

package com.sharded.core.modules.nametags;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class NametagsRefreshTest {
    @Test
    void nametagRefreshIsAtLeastOneSecond() throws IOException {
        String yaml = Files.readString(
                Path.of("overlay/resources/modules/nametags/config.yml"),
                StandardCharsets.UTF_8);
        assertTrue(yaml.contains("config-version: 4"));
        assertTrue(yaml.contains("refresh: 20"));
        String source = Files.readString(
                Path.of("overlay/src/com/sharded/core/modules/nametags/NametagsModule.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("Math.max(20L, this.config.getLong(\"refresh\", 20L))"));
        assertTrue(source.contains("clampRefresh()"));
        assertTrue(source.contains("lastText"));
    }
}

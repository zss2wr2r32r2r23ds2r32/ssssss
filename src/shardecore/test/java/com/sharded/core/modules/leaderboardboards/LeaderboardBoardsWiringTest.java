package com.sharded.core.modules.leaderboardboards;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LeaderboardBoardsWiringTest {
    @Test
    void pluginYmlDeclaresHologramCommands() throws IOException {
        String yaml = Files.readString(Path.of("overlay/plugin.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("version: '1.6.91'"));
        assertTrue(yaml.contains("  lb:"));
        assertTrue(yaml.contains("  lbsetline:"));
        assertTrue(yaml.contains("aliases: [setline, lbline]"));
        assertTrue(yaml.contains("sharded.leaderboard.admin:"));
        assertTrue(yaml.contains("usage: /leaderboard [type|create|delete|reload|setlocation]"));
    }

    @Test
    void boardsYamlUsesHeadPlaceholdersNotTextHeads() throws IOException {
        String yaml = Files.readString(
                Path.of("overlay/resources/modules/leaderboardboards/boards.yml"),
                StandardCharsets.UTF_8);
        assertTrue(yaml.contains("%leaderboard_kills_head_1%"));
        assertTrue(yaml.contains("%leaderboard_kills_name_1%"));
        assertTrue(yaml.contains("%leaderboard_kills_value_1%"));
        assertTrue(yaml.contains("%leaderboard_elo_head_1%"));
        assertFalse(yaml.contains("[head]"));
        assertFalse(yaml.contains("%head% &#FF007B#1"));
    }

    @Test
    void mainConfigEnablesHologramBoardsWithoutOldTopper() throws IOException {
        String yaml = Files.readString(Path.of("overlay/resources/config.yml"), StandardCharsets.UTF_8);
        assertTrue(yaml.contains("leaderboardboards: true"));
        assertTrue(yaml.contains("leaderboardtopper: false"));
        assertTrue(yaml.contains("config-version: 26"));
    }
}

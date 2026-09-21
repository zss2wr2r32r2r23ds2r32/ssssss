package com.sharded.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paper on Java 25 failed to enable 1.6.76 because the fat-jar copy of
 * ShardedCore still constructed LeaderboardTopperModule, which had been
 * stripped from the zip. Overlay main must not do that.
 */
final class OverlayMainClassRefsTest {
    @Test
    void overlayMainClassDoesNotConstructRemovedModules() throws IOException {
        String source = Files.readString(
                Path.of("overlay/src/com/sharded/core/ShardedCore.java"),
                StandardCharsets.UTF_8);
        assertFalse(source.contains("import com.sharded.core.modules.leaderboardtopper"));
        assertFalse(source.contains("new LeaderboardTopperModule"));
        assertFalse(source.contains("new MultiverseModule"));
        assertTrue(source.contains("modules.leaderboardtopper"));
        assertTrue(source.contains("modules.multiverse"));
        assertTrue(source.contains("new ItemEditModule"));
        assertTrue(source.contains("new LeaderboardBoardsModule"));
        assertFalse(source.contains("new HideModule"));
        assertFalse(source.contains("new GravesModule"));
    }
}

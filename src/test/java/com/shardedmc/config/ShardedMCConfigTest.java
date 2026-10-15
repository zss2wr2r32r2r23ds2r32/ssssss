package com.shardedmc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ShardedMCConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsAndPersistsDefaults() throws Exception {
        Path path = tempDir.resolve("shardedmc.yml");
        ShardedMCConfig original = ShardedMCConfig.defaults();
        original.save(path);

        ShardedMCConfig loaded = ShardedMCConfig.load(path);
        assertTrue(loaded.getSharding().isEnabled());
        assertEquals(8, loaded.getSharding().getRegionSize());
        assertTrue(loaded.getChunks().isAsyncLoading());
        assertEquals(2048, loaded.getChunks().getCacheSize());
        assertEquals("adaptive", loaded.getNetwork().getCompressionLevel());
    }
}

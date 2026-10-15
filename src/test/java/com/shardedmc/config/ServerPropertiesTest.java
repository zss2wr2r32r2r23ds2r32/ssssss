package com.shardedmc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ServerPropertiesTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsAndPersistsDefaults() throws Exception {
        Path path = tempDir.resolve("server.properties");
        ServerProperties original = ServerProperties.defaults();
        original.save(path);

        ServerProperties loaded = ServerProperties.load(path);
        assertEquals(original.getMotd(), loaded.getMotd());
        assertEquals(original.getServerPort(), loaded.getServerPort());
        assertEquals(original.getMaxPlayers(), loaded.getMaxPlayers());
        assertTrue(loaded.isOnlineMode());
    }
}

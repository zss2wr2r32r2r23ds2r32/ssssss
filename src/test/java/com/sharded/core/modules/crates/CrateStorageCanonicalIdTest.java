package com.sharded.core.modules.crates;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateStorageCanonicalIdTest {
    @Test
    void excellentCratesIdsMapOntoShippedCrates() {
        assertEquals("keyall", CrateStorage.canonicalId("KEYALL"));
        assertEquals("keyall", CrateStorage.canonicalId("keyall_crate"));
        assertEquals("shard", CrateStorage.canonicalId("SHARD"));
        assertEquals("shard", CrateStorage.canonicalId("sharded"));
        assertEquals("shard", CrateStorage.canonicalId("shard_crate"));
        assertEquals("killkey", CrateStorage.canonicalId("KILL_CRATE"));
        assertEquals("killkey", CrateStorage.canonicalId("kill"));
        assertEquals("vote", CrateStorage.canonicalId("VOTE_CRATE"));
        assertEquals("astral", CrateStorage.canonicalId("astral"));
        assertEquals("summer", CrateStorage.canonicalId("SUMMER"));
        assertEquals("koth", CrateStorage.canonicalId("KOTH"));
        assertEquals("outpost", CrateStorage.canonicalId("OUTPOST"));
    }

    @Test
    void cratesCommandListsInsteadOfOpeningAGui() throws IOException {
        String src = Files.readString(
                Path.of("overlay/src/com/sharded/core/modules/crates/CratesModule.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("this.list(sender);"));
        assertFalse(src.contains("openBrowse"));
        assertFalse(src.contains("browseIcon"));
        assertTrue(src.contains("DefaultCrates.install"));

        String yaml = Files.readString(
                Path.of("overlay/resources/modules/crates/config.yml"),
                StandardCharsets.UTF_8);
        assertTrue(yaml.contains("list crates (players see their key counts)"));
        assertFalse(yaml.contains("open the crate browser GUI"));
        assertTrue(yaml.contains("keyall, shard, killkey"));
    }

    @Test
    void defaultCratesSeedKeyallAndShard() throws IOException {
        String src = Files.readString(
                Path.of("overlay/src/com/sharded/core/modules/crates/DefaultCrates.java"),
                StandardCharsets.UTF_8);
        assertTrue(src.contains("\"keyall\""));
        assertTrue(src.contains("\"shard\""));
        assertTrue(src.contains("\"killkey\""));
        assertTrue(src.contains("Keyall crate"));
        assertTrue(src.contains("Shard crate"));
        assertTrue(src.contains("tokens give %player%"));
        assertTrue(src.contains("crate key give %player% shard"));
    }
}

package com.sharded.core.modules.crates;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateRewardItemsTest {
    @Test
    void cleanStripsLeadingSlashes() {
        assertEquals("tokens give %player% 100", CrateRewardItems.clean("/tokens give %player% 100"));
        assertEquals("lp user %player% parent add vip", CrateRewardItems.clean("lp user %player% parent add vip"));
        assertEquals("", CrateRewardItems.clean("   "));
    }

    @Test
    void cancelWordsAreRecognised() {
        assertTrue(CrateRewardItems.isCancel("cancel"));
        assertTrue(CrateRewardItems.isCancel("/stop"));
        assertTrue(CrateRewardItems.isCancel("EXIT"));
        assertFalse(CrateRewardItems.isCancel("tokens give %player% 1"));
    }

    @Test
    void cratesConfigDocumentsCommandRewards() throws IOException {
        String yaml = Files.readString(
                Path.of("overlay/resources/modules/crates/config.yml"),
                StandardCharsets.UTF_8);
        assertTrue(yaml.contains("config-version: 16"));
        assertTrue(yaml.contains("instant-claim: true"));
        assertTrue(yaml.contains("picks: 1"));
        assertTrue(yaml.contains("/crate open <crate>"));
        assertTrue(yaml.contains("/crate editor"));
        assertTrue(yaml.contains("list crates"));
        assertTrue(yaml.contains("colored shulker"));
        assertTrue(yaml.contains("usage-add:"));
        assertTrue(yaml.contains("usage-command:"));
        assertTrue(yaml.contains("command-prompt:"));
        assertTrue(yaml.contains("place-item:"));
        assertTrue(yaml.contains("reward-removed:"));
        assertTrue(yaml.contains("updated-item:"));
        assertTrue(yaml.contains("remove-cancelled:"));
        assertTrue(yaml.contains("GUI editor"));
        assertTrue(yaml.contains("pick ITEM or COMMAND") || yaml.contains("ITEM or COMMAND"));
        assertTrue(yaml.contains("Nexo"));
        assertTrue(yaml.contains("/crate add <crate>"));
        assertTrue(yaml.contains("%player%"));
    }

    @Test
    void chatLabelNeverSaysCommand() {
        assertEquals("Cool Sword", CrateRewardItems.chatLabel("Cool Sword", "tokens give %player% 1", null));
        assertEquals("tokens give %player% 100",
                CrateRewardItems.chatLabel("COMMAND", "tokens give %player% 100", null));
        assertEquals("Diamond Sword", CrateRewardItems.chatLabel("Diamond Sword", null, null));
        assertFalse(CrateRewardItems.chatLabel("COMMAND", "crates key give %player% vote 1", null)
                .equalsIgnoreCase("COMMAND"));
    }
}

package com.sharded.core.modules.tags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TagIdsTest {
    @Test
    void creatorAndLegacyStreamerIdsAreTheHiddenTag() {
        assertTrue(TagIds.isCreatorTag("creator"));
        assertTrue(TagIds.isCreatorTag("CREATOR"));
        assertTrue(TagIds.isCreatorTag("streamer"));
        assertFalse(TagIds.isCreatorTag("sigma"));
        assertFalse(TagIds.isCreatorTag(null));
    }
}

package com.sharded.core.modules.leaderboardboards;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlaceholderParserTest {
    @Test
    void parsesNameValueUuidHead() {
        var name = PlaceholderParser.parse("kills_name_1");
        assertNotNull(name);
        assertEquals("kills", name.key());
        assertEquals("name", name.field());
        assertEquals(1, name.position());

        var head = PlaceholderParser.parse("%leaderboard_kills_head_2%");
        assertNotNull(head);
        assertTrue(head.isHead());
        assertEquals(2, head.position());

        var glued = PlaceholderParser.parse("leaderboard_killshead_3");
        assertNotNull(glued);
        assertEquals("kills", glued.key());
        assertTrue(glued.isHead());
        assertEquals(3, glued.position());

        var swapped = PlaceholderParser.parse("tokens_5_value");
        assertNotNull(swapped);
        assertEquals("tokens", swapped.key());
        assertEquals("value", swapped.field());
        assertEquals(5, swapped.position());
    }

    @Test
    void lineStripsHeadPlaceholderAndKeepsRank() {
        var spec = PlaceholderParser.line(
                "%leaderboard_kills_head_1% &#FF007B#1 &f%leaderboard_kills_name_1%");
        assertTrue(spec.hasHead());
        assertEquals(1, spec.headRank());
        assertFalse(spec.text().contains("head"));
        assertTrue(spec.text().contains("%leaderboard_kills_name_1%"));
        assertFalse(PlaceholderParser.isSpacer(spec.text()));
        assertTrue(PlaceholderParser.isSpacer("&r"));
        assertNull(PlaceholderParser.parse("not-a-placeholder"));
    }
}

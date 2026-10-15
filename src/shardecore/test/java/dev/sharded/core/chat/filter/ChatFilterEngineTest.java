package dev.sharded.core.chat.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatFilterEngineTest {
    private ChatFilterEngine engine(FilterConfig config, WordRule... rules) {
        return new ChatFilterEngine(config, List.of(rules));
    }

    @Test
    void slowmodeBlocksSecondMessage() {
        FilterConfig config = new FilterConfig();
        ChatFilterEngine engine = engine(config);
        PlayerFilterState state = new PlayerFilterState();
        FilterVerdict first = engine.evaluate("hello there", 1_000L, state);
        assertFalse(first.blocked());
        FilterVerdict second = engine.evaluate("next", 1_000L + 1_500L, state);
        assertTrue(second.blocked());
        assertEquals("slowmode", second.rule());
        assertEquals(2, second.waitSeconds());
        FilterVerdict third = engine.evaluate("next", 1_000L + 3_000L, state);
        assertFalse(third.blocked());
    }

    @Test
    void lengthAndSameCharSpam() {
        FilterConfig config = new FilterConfig();
        ChatFilterEngine engine = engine(config);
        assertEquals("length", engine.test("x".repeat(129)).rule());
        assertEquals("spamming", engine.test("aaaaaa").rule());
        assertFalse(engine.test("aaaaa").blocked());
    }

    @Test
    void repeatUsesMatchPercent() {
        FilterConfig config = new FilterConfig();
        ChatFilterEngine engine = engine(config);
        PlayerFilterState state = new PlayerFilterState();
        engine.evaluate("selling cheap kits today", 10_000L, state);
        FilterVerdict repeat = engine.evaluate("selling cheap kits today!", 14_000L, state);
        assertTrue(repeat.blocked());
        assertEquals("repeat", repeat.rule());
    }

    @Test
    void shoutingLowercasesThenAllows() {
        FilterConfig config = new FilterConfig();
        ChatFilterEngine engine = engine(config);
        FilterVerdict verdict = engine.test("THIS IS A VERY LOUD MESSAGE");
        assertFalse(verdict.blocked());
        assertTrue(verdict.modified());
        assertEquals("shouting", verdict.rule());
        assertEquals("LOWERCASE", verdict.action());
        assertEquals("this is a very loud message", verdict.result());
    }

    @Test
    void shoutingCancelDrops() {
        FilterConfig config = new FilterConfig();
        config.shoutingAction = FilterConfig.ShoutingAction.CANCEL;
        ChatFilterEngine engine = engine(config);
        FilterVerdict verdict = engine.test("THIS IS A VERY LOUD MESSAGE");
        assertTrue(verdict.blocked());
        assertEquals("shouting", verdict.rule());
    }

    @Test
    void cancelledWordBlocksBeforeMask() {
        FilterConfig config = new FilterConfig();
        WordRule blocked = new WordRule("blocked", WordRule.Action.CANCEL, List.of("badwordxyz"), List.of());
        WordRule masked = new WordRule("masked", WordRule.Action.MASK, List.of("oops"), List.of());
        ChatFilterEngine engine = engine(config, blocked, masked);
        FilterVerdict hit = engine.test("that badwordxyz is not ok");
        assertTrue(hit.blocked());
        assertEquals("blocked", hit.rule());
        assertEquals("CANCEL", hit.action());
    }

    @Test
    void maskReplacesAndSends() {
        FilterConfig config = new FilterConfig();
        WordRule masked = new WordRule("masked", WordRule.Action.MASK, List.of("oopsie"), List.of());
        ChatFilterEngine engine = engine(config, masked);
        FilterVerdict hit = engine.test("an oopsie happened");
        assertFalse(hit.blocked());
        assertTrue(hit.modified());
        assertEquals("MASK", hit.action());
        assertTrue(hit.result().contains("***"));
        assertFalse(hit.result().toLowerCase().contains("oopsie"));
    }

    @Test
    void advertisingRegexCancels() {
        FilterConfig config = new FilterConfig();
        WordRule ads = new WordRule("advertising", WordRule.Action.CANCEL, List.of(),
                List.of("(?i)discord\\.gg/[A-Za-z0-9-]+"));
        ChatFilterEngine engine = engine(config, ads);
        assertTrue(engine.test("join discord.gg/sharded").blocked());
        assertFalse(engine.test("hello friends").blocked());
    }

    @Test
    void firstBlockingCheckWins() {
        FilterConfig config = new FilterConfig();
        ChatFilterEngine engine = engine(config, new WordRule("blocked", WordRule.Action.CANCEL, List.of("nope"), List.of()));
        PlayerFilterState state = new PlayerFilterState();
        engine.evaluate("ok", 0L, state);
        FilterVerdict second = engine.evaluate("nope", 500L, state);
        assertEquals("slowmode", second.rule());
    }
}

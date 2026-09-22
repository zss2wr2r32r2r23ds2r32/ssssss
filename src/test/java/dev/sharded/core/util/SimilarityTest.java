package dev.sharded.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimilarityTest {
    @Test
    void uniqueLettersAndRuns() {
        assertEquals(6, Similarity.uniqueLetterCount("bookkeeper"));
        assertEquals(1, Similarity.uniqueLetterCount("aa"));
        assertEquals(5, Similarity.longestSameInARow("baaaaab"));
        assertEquals(5, Similarity.uppercaseCount("AaBbCcDD"));
    }

    @Test
    void identicalMessagesAre100() {
        assertEquals(100, Similarity.percent("Hello there", "hello there"));
        assertTrue(Similarity.percent("selling kits now", "selling kits now!") >= 80);
        assertTrue(Similarity.percent("apple", "orange") < 80);
    }

    @Test
    void miniMessageConversion() {
        String mini = ColorUtil.toMiniMessage("&#FF0000&lCHAT &7▷ &fWait");
        assertTrue(mini.contains("<#FF0000>"));
        assertTrue(mini.contains("<bold>"));
        assertTrue(mini.contains("<gray>"));
        assertTrue(mini.contains("▷"));
    }
}

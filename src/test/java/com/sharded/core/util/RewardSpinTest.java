package com.sharded.core.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class RewardSpinTest {
    @Test
    void replacesUppercaseRarityColorPlaceholder() {
        RewardSpin.RewardOption option = new RewardSpin.RewardOption(
                "75 Tokens", "COMMON", "&#A370EE", 35.0, 35.0, List.of());
        String formatted = RewardSpin.applyFormat(
                "WEEKLY ▷ YOU WON %reward% %RARITY_COLOR%[%rarity% (%percent%% %RARITY_COLOR%)]!",
                option);
        assertFalse(formatted.contains("%RARITY_COLOR%"));
        assertFalse(formatted.contains("%rarity_color%"));
        assertEquals(
                "WEEKLY ▷ YOU WON 75 Tokens &#A370EE[COMMON (35% &#A370EE)]!",
                formatted);
    }
}

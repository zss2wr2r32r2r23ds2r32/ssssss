package dev.sharded.velocitycore.lobby.motd;

import dev.sharded.velocitycore.lobby.util.TextParser;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class HoverLines {

    private static final int MAX_LEGACY_LENGTH = 40;
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private HoverLines() {
    }

    public static String format(String raw, int onlinePlayers, int maxPlayers) {
        if (raw == null) {
            return "";
        }
        String replaced = raw
                .replace("{online_players}", String.valueOf(onlinePlayers))
                .replace("{max_players}", String.valueOf(maxPlayers));
        if (replaced.isEmpty()) {
            return "";
        }
        return truncateLegacy(LEGACY_SECTION.serialize(TextParser.parse(replaced)));
    }

    static String truncateLegacy(String legacy) {
        if (legacy.length() <= MAX_LEGACY_LENGTH) {
            return legacy;
        }
        String truncated = legacy.substring(0, MAX_LEGACY_LENGTH);
        while (truncated.endsWith("\u00A7") || truncated.endsWith("&")) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        if (truncated.contains("\u00A7x")) {
            int sectionIndex = truncated.lastIndexOf('\u00A7');
            if (sectionIndex >= 0 && sectionIndex + 1 < truncated.length()) {
                char code = truncated.charAt(sectionIndex + 1);
                if (code == 'x' || (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || (code >= 'A' && code <= 'F')
                        || (code >= 'k' && code <= 'o') || code == 'r' || code == 'K' || code == 'L' || code == 'M'
                        || code == 'N' || code == 'O' || code == 'R') {
                    if (code == 'x') {
                        int hexStart = truncated.lastIndexOf("\u00A7x");
                        if (hexStart >= 0 && truncated.length() - hexStart < 14) {
                            truncated = truncated.substring(0, hexStart);
                        }
                    } else if (sectionIndex + 2 == truncated.length()) {
                        truncated = truncated.substring(0, sectionIndex);
                    }
                }
            }
        }
        return truncated;
    }
}

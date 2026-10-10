package com.sharded.core.util;

import java.util.Locale;

/** Rainbow legacy colour cycling for chat messages. */
public final class RainbowUtil {

    private static final String[] RAINBOW = {
            "&#FF0000", "&#FF7F00", "&#FFFF00", "&#00FF00", "&#0000FF", "&#4B0082", "&#9400D3"
    };

    private RainbowUtil() {
    }

    public static String apply(String text) {
        if (text == null || text.isEmpty()) return "";
        String plain = ColorUtil.normalize(text)
                .replaceAll("(?i)&#[0-9a-f]{6}", "")
                .replaceAll("(?i)&[0-9a-fk-or]", "")
                .replace("§", "");
        if (plain.isEmpty()) return text;
        StringBuilder out = new StringBuilder();
        int colorIndex = 0;
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            if (c == ' ') {
                out.append(' ');
                continue;
            }
            out.append(RAINBOW[colorIndex % RAINBOW.length]).append(c);
            colorIndex++;
        }
        return out.toString();
    }
}

package com.sharded.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts DeluxeMenus-style &amp;x&amp;R&amp;R&amp;G&amp;G&amp;B&amp;B to &amp;#RRGGBB. */
public final class ColorUtil {

    private static final Pattern EXTENDED_HEX = Pattern.compile("&x((?:&[0-9a-fA-F]){6})");

    private ColorUtil() {
    }

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        Matcher matcher = EXTENDED_HEX.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String group = matcher.group(1);
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < group.length(); i += 2) {
                if (group.charAt(i) == '&' && i + 1 < group.length()) {
                    hex.append(group.charAt(i + 1));
                }
            }
            matcher.appendReplacement(out, "&#" + hex);
        }
        matcher.appendTail(out);
        return out.toString();
    }
}

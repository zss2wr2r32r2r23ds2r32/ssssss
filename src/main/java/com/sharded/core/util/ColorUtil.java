package com.sharded.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts &amp;x&amp;R&amp;R&amp;G&amp;G&amp;B&amp;B and bare hex to legacy &amp;#RRGGBB for Adventure. */
public final class ColorUtil {

    private static final Pattern EXTENDED_HEX = Pattern.compile("(?i)&x((?:&[0-9a-fA-F]){6})");
    private static final Pattern BARE_HEX = Pattern.compile("(?<![0-9a-fA-F#])#([0-9a-fA-F]{6})(?![0-9a-fA-F])");
    private static final Pattern LEGACY_BROKEN = Pattern.compile("(?i)[&§][0-9a-fk-orx]#([0-9a-fA-F]{6})");
    /** Truncated &amp;x&amp;5&amp;0 style fragments from broken YAML. */
    private static final Pattern TRUNCATED_HEX = Pattern.compile("(?i)&x((?:&[0-9a-fA-F]){1,5})(?=&[^0-9a-fA-F]|$|\\s)");

    private ColorUtil() {
    }

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        String normalized = input.replace('§', '&');
        normalized = fixBrokenHex(normalized);
        normalized = convertExtendedHex(normalized);
        return normalized;
    }

    private static String convertExtendedHex(String input) {
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
            if (hex.length() == 6) {
                matcher.appendReplacement(out, Matcher.quoteReplacement("&#" + hex));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String fixBrokenHex(String input) {
        Matcher legacy = LEGACY_BROKEN.matcher(input);
        StringBuilder fixed = new StringBuilder();
        while (legacy.find()) {
            legacy.appendReplacement(fixed, Matcher.quoteReplacement("&#" + legacy.group(1)));
        }
        legacy.appendTail(fixed);
        input = fixed.toString();

        Matcher bare = BARE_HEX.matcher(input);
        StringBuilder out = new StringBuilder();
        while (bare.find()) {
            bare.appendReplacement(out, Matcher.quoteReplacement("&#" + bare.group(1)));
        }
        bare.appendTail(out);
        input = out.toString();

        // Drop orphaned truncated &x fragments that would show literally in chat.
        return TRUNCATED_HEX.matcher(input).replaceAll("");
    }
}

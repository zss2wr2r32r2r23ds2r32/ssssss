package com.sharded.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts &amp;x&amp;R&amp;R&amp;G&amp;G&amp;B&amp;B to &amp;#RRGGBB for Adventure legacy serializer. */
public final class ColorUtil {

    private static final Pattern EXTENDED_HEX = Pattern.compile("(?i)&x((?:&[0-9a-fA-F]){6})");
    /** &#RRGGBB (exactly 6 hex digits). */
    private static final Pattern FULL_HEX = Pattern.compile("(?i)&#([0-9a-fA-F]{6})");
    /** Mistaken &#f / &#7 — only 1 hex digit, should be legacy &f / &7. */
    private static final Pattern SHORT_HEX = Pattern.compile("(?i)&#([0-9a-fA-F])(?![0-9a-fA-F])");
    private static final Pattern DOUBLE_AMP = Pattern.compile("&&+");

    private ColorUtil() {
    }

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input.replace('§', '&');
        s = DOUBLE_AMP.matcher(s).replaceAll("&");
        s = convertExtendedHex(s);
        s = fixShortHex(s);
        return s;
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

    /** &#f → &f so placeholders like &f%player% are not eaten by the hex parser. */
    private static String fixShortHex(String input) {
        return SHORT_HEX.matcher(input).replaceAll("&$1");
    }
}

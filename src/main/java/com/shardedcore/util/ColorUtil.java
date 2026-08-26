package com.shardedcore.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {

    private static final Pattern EXTENDED_HEX = Pattern.compile("(?i)&x((?:&[0-9a-fA-F]){6})");
    private static final Pattern FULL_HEX = Pattern.compile("(?i)&#([0-9a-fA-F]{6})");
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
        s = uppercaseBoldWords(s);
        return s;
    }

    public static String normalizePlain(String input) {
        if (input == null || input.isEmpty()) return "";
        String s = input.replace('§', '&');
        s = DOUBLE_AMP.matcher(s).replaceAll("&");
        s = convertExtendedHex(s);
        s = fixShortHex(s);
        return s;
    }

    public static String hexToLegacy(String input) {
        return hexToLegacy(input, true);
    }

    public static String hexToLegacyPlain(String input) {
        return hexToLegacy(input, false);
    }

    private static String hexToLegacy(String input, boolean uppercaseBold) {
        if (input == null || input.isEmpty()) return "";
        String s = uppercaseBold ? normalize(input) : normalizePlain(input);
        Matcher matcher = FULL_HEX.matcher(s);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder legacy = new StringBuilder("&x");
            for (char c : hex.toCharArray()) {
                legacy.append('&').append(c);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String uppercaseBoldWords(String input) {
        if (input == null || input.isEmpty()) return "";
        Pattern boldWord = Pattern.compile("(?i)&l([A-Za-z][A-Za-z0-9'\\-_ ]*)");
        Matcher matcher = boldWord.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(
                    "&l" + matcher.group(1).toUpperCase(Locale.ROOT)));
        }
        matcher.appendTail(out);
        return out.toString();
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

    private static String fixShortHex(String input) {
        return SHORT_HEX.matcher(input).replaceAll("&$1");
    }
}

package com.sharded.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tag text for tab/nametag and accent colours for GUI lore. */
public final class TagDisplayUtil {

    private static final Pattern HEX_COLOR = Pattern.compile(
            "(&x(?:&[0-9A-Fa-f]){6}|&#[0-9A-Fa-f]{6}|&[0-9a-f])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_TAG_WORD = Pattern.compile("\\s+tag\\s*$", Pattern.CASE_INSENSITIVE);

    private TagDisplayUtil() {
    }

    /** Bracket tag only — strips GUI suffixes like trailing \" Tag\". */
    public static String tabTag(String raw) {
        if (raw == null || raw.isBlank()) return "";
        String tag = ColorUtil.normalize(raw.trim());
        tag = TRAILING_TAG_WORD.matcher(tag).replaceAll("");
        return tag.trim();
    }

    /** First hex or bright colour in a tag string for lore accents (skips &7/&8 brackets). */
    public static String accentColor(String raw) {
        if (raw == null || raw.isBlank()) return "&x&F&F&B&A&0&0";
        String normalized = ColorUtil.normalize(raw);
        Matcher matcher = HEX_COLOR.matcher(normalized);
        while (matcher.find()) {
            String code = matcher.group(1);
            if (code.equalsIgnoreCase("&8") || code.equalsIgnoreCase("&7")) continue;
            if (code.startsWith("&x") || code.startsWith("&#")) return code;
            if (code.length() == 2 && "0123456789abcdef".indexOf(Character.toLowerCase(code.charAt(1))) >= 0) {
                return code;
            }
        }
        return "&x&F&F&B&A&0&0";
    }

    public static String loreLine(String accent, String text) {
        return accent + text;
    }
}

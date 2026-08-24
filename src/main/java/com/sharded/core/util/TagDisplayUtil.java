package com.sharded.core.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tag text for tab/nametag and accent colours for GUI lore. */
public final class TagDisplayUtil {

    private static final Pattern HEX_COLOR = Pattern.compile(
            "(&x(?:&[0-9A-Fa-f]){6}|&#[0-9A-Fa-f]{6}|&[0-9a-fk-or])",
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

    /** First colour code in a tag string for lore accents. */
    public static String accentColor(String raw) {
        if (raw == null || raw.isBlank()) return "&x&F&F&B&A&0&0";
        Matcher matcher = HEX_COLOR.matcher(ColorUtil.normalize(raw));
        if (matcher.find()) return matcher.group(1);
        return "&x&F&F&B&A&0&0";
    }

    public static String loreLine(String accent, String text) {
        return accent + text;
    }
}

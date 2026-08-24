package com.sharded.core.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves colour values from module config (supports legacy external-plugin command strings). */
public final class ColorConfigUtil {

    private static final Pattern HEX_IN_COMMAND = Pattern.compile("#([0-9A-Fa-f]{6})");

    private ColorConfigUtil() {
    }

    public static String resolveValue(ConfigurationSection section, String defaultValue) {
        if (section == null) return defaultValue;
        String value = section.getString("value");
        if (value != null && !value.isBlank() && !looksLikeLegacyCommand(value)) {
            return value.trim();
        }
        String command = section.getString("command");
        if (command != null && !command.isBlank()) {
            String parsed = parseLegacyCommand(command);
            if (parsed != null) return parsed;
        }
        return defaultValue;
    }

    public static String resolvePermission(String id, ConfigurationSection section, String prefix) {
        String perm = section.getString("permission", prefix + id);
        if (perm.startsWith("namecolor.set.color.")) return "sharded.namecolor." + id;
        if (perm.startsWith("ezcolor.color.")) return "sharded.chatcolor." + id;
        if (perm.startsWith("eternaltags.tag.")) return "sharded.tag." + id;
        return perm;
    }

    private static boolean looksLikeLegacyCommand(String value) {
        String lower = value.toLowerCase();
        return lower.contains("namecolor:") || lower.startsWith("ezcolor") || lower.contains("eternaltags");
    }

    private static String parseLegacyCommand(String command) {
        String lower = command.toLowerCase();
        if (lower.contains("rainbow")) return "rainbow";
        Matcher matcher = HEX_IN_COMMAND.matcher(command);
        if (matcher.find()) return "&#" + matcher.group(1);
        return null;
    }
}

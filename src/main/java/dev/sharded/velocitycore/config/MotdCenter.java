package dev.sharded.velocitycore.config;

import java.util.regex.Pattern;

public final class MotdCenter {

    private static final Pattern COLOR_PATTERN = Pattern.compile("(&#[0-9A-Fa-f]{6})|(&[0-9A-Fa-fK-Ok-or])|(§#[0-9A-Fa-f]{6})|(§[0-9A-Fa-fK-Ok-or])");

    private MotdCenter() {
    }

    public static String center(String line, int width) {
        if (line == null || line.isBlank()) {
            return line;
        }
        int visible = visibleLength(line);
        int padding = Math.max(0, (width - visible) / 2);
        return " ".repeat(padding) + line;
    }

    public static int visibleLength(String input) {
        String stripped = COLOR_PATTERN.matcher(input).replaceAll("");
        stripped = stripped.replaceAll("§x(§[0-9a-fA-F]){6}", "");
        stripped = stripped.replaceAll("<[^>]+>", "");
        return stripped.length();
    }
}

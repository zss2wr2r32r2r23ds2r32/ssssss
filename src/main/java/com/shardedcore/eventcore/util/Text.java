package com.shardedcore.eventcore.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Colour/placeholder pipeline for every user facing string in the plugin.
 *
 * <p>Supports the three colour styles used across the configuration files:
 * legacy codes ({@code &a}), short hex ({@code &#AD4EFF}) and the "unusual"
 * repeated-character hex format ({@code &x&F&F&B&A&0&0}).</p>
 *
 * <p>Parsing a legacy string allocates a component tree, which is far too
 * expensive to redo for every GUI repaint or title frame. Strings that contain
 * no placeholders are therefore memoised; the cache is bounded so a plugin
 * reload with wildly different configuration can never leak.</p>
 */
public final class Text {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private static final int CACHE_LIMIT = 2048;
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private Text() {
    }

    /** Parses a raw configuration string, suppressing the italic default applied to item text. */
    public static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        Component cached = CACHE.get(raw);
        if (cached != null) {
            return cached;
        }
        Component parsed = SERIALIZER.deserialize(raw).decoration(TextDecoration.ITALIC, false);
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(raw, parsed);
        return parsed;
    }

    /** Parses a raw string after substituting {@code %key%} tokens. */
    public static Component parse(String raw, Map<String, String> placeholders) {
        return parse(fill(raw, placeholders));
    }

    /** Parses a raw string after substituting a single {@code %key%} token. */
    public static Component parse(String raw, String key, String value) {
        return parse(fill(raw, key, value));
    }

    public static List<Component> parseList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(parse(line));
        }
        return out;
    }

    public static List<Component> parseList(List<String> raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(parse(line, placeholders));
        }
        return out;
    }

    /**
     * Expands a lore template. A template line may itself contain newlines so a
     * single configuration entry can produce a multi-line block.
     */
    public static List<Component> parseLore(List<String> raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        List<Component> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            String filled = fill(line, placeholders);
            if (filled.indexOf('\n') < 0) {
                out.add(parse(filled));
                continue;
            }
            for (String part : filled.split("\n", -1)) {
                out.add(parse(part));
            }
        }
        return out;
    }

    public static String fill(String raw, Map<String, String> placeholders) {
        if (raw == null || raw.isEmpty() || placeholders == null || placeholders.isEmpty()) {
            return raw == null ? "" : raw;
        }
        if (raw.indexOf('%') < 0) {
            return raw;
        }
        String out = raw;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            out = out.replace(entry.getKey(), entry.getValue());
        }
        return out;
    }

    public static String fill(String raw, String key, String value) {
        if (raw == null || raw.isEmpty() || raw.indexOf('%') < 0) {
            return raw == null ? "" : raw;
        }
        return raw.replace(key, value);
    }

    /** Flattens a component back to plain text, used for log output and inventory titles. */
    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static void invalidateCache() {
        CACHE.clear();
    }
}

package com.shardedcore.eventcore.config;

import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central lookup for every operator-editable message.
 *
 * <p>Every string in {@code messages.yml} runs through the same colour parser as
 * the GUIs, and an empty value silently suppresses the message so operators can
 * mute individual feedback lines.</p>
 */
public final class Messages {

    private final ConfigFile file;
    private String prefix = "";

    public Messages(ConfigFile file) {
        this.file = file;
        reload();
    }

    public void reload() {
        file.reload();
        prefix = file.raw().getString("prefix", "");
    }

    public String rawPrefix() {
        return prefix;
    }

    /** Builds a placeholder map from alternating key/value varargs. */
    public static Map<String, String> placeholders(String... pairs) {
        if (pairs.length == 0) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>(Math.max(4, pairs.length));
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    public String raw(String key) {
        return file.raw().getString(key, "");
    }

    public List<String> rawList(String key) {
        return file.raw().getStringList(key);
    }

    public Component component(String key, Map<String, String> placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            return Component.empty();
        }
        Map<String, String> merged = withPrefix(placeholders);
        return Text.parse(raw, merged);
    }

    public void send(CommandSender sender, String key, String... pairs) {
        send(sender, key, placeholders(pairs));
    }

    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = raw(key);
        if (raw.isEmpty()) {
            List<String> lines = rawList(key);
            if (lines.isEmpty()) {
                return;
            }
            Map<String, String> merged = withPrefix(placeholders);
            for (String line : lines) {
                sender.sendMessage(Text.parse(line, merged));
            }
            return;
        }
        sender.sendMessage(Text.parse(raw, withPrefix(placeholders)));
    }

    public void broadcast(String key, String... pairs) {
        broadcast(key, placeholders(pairs));
    }

    public void broadcast(String key, Map<String, String> placeholders) {
        String raw = raw(key);
        Map<String, String> merged = withPrefix(placeholders);
        if (!raw.isEmpty()) {
            Component message = Text.parse(raw, merged);
            Bukkit.getServer().sendMessage(message);
            return;
        }
        List<String> lines = rawList(key);
        if (lines.isEmpty()) {
            return;
        }
        Audience audience = Bukkit.getServer();
        for (String line : lines) {
            audience.sendMessage(Text.parse(line, merged));
        }
    }

    private Map<String, String> withPrefix(Map<String, String> placeholders) {
        if (placeholders == null || placeholders.isEmpty()) {
            return Map.of("%prefix%", prefix);
        }
        Map<String, String> merged = new HashMap<>(placeholders);
        merged.put("%prefix%", prefix);
        return merged;
    }
}

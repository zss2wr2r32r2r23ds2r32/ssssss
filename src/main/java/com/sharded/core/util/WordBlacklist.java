package com.sharded.core.util;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.List;
import java.util.Locale;

/** Shared swear-word filter used by toolname, pets, etc. */
public final class WordBlacklist {

    private WordBlacklist() {
    }

    public static boolean contains(YamlConfiguration config, String listKey, String input) {
        if (input == null || input.isBlank()) return false;
        String lower = input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        List<String> blacklist = config.getStringList(listKey);
        for (String word : blacklist) {
            if (word.isBlank()) continue;
            if (lower.contains(word.toLowerCase(Locale.ROOT).trim())) return true;
        }
        return false;
    }
}

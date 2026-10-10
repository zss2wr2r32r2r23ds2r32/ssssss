package com.shardedcore.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class Tabs {

    private Tabs() {
    }

    public static List<String> filter(Collection<String> options, String token) {
        if (options == null || options.isEmpty()) return List.of();
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(option);
        }
        return out;
    }

    public static List<String> filter(List<String> options, String token) {
        return filter((Collection<String>) options, token);
    }

    public static List<String> players(String token) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        return org.bukkit.Bukkit.getOnlinePlayers().stream()
                .map(org.bukkit.entity.Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }
}

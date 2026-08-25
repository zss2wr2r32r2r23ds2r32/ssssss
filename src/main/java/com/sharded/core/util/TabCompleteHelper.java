package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class TabCompleteHelper {

    private TabCompleteHelper() {
    }

    public static List<String> filter(String input, String... options) {
        return filter(input, Arrays.asList(options));
    }

    public static List<String> filter(String input, Collection<String> options) {
        String needle = input == null ? "" : input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) out.add(option);
        }
        return out;
    }

    public static List<String> onlinePlayers(String input) {
        return onlinePlayers(input, false);
    }

    public static List<String> onlinePlayers(String input, boolean offlineAdmins) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        if (offlineAdmins) {
            for (var offline : Bukkit.getOfflinePlayers()) {
                if (offline.getName() != null && offline.isOnline()) continue;
                if (offline.getName() != null) names.add(offline.getName());
            }
        }
        return filter(input, names);
    }

    public static List<String> ifPermission(CommandSender sender, String permission, String input, String... options) {
        if (!sender.hasPermission(permission)) return List.of();
        return filter(input, options);
    }

    public static List<String> knownPlayers(String input) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        for (var offline : Bukkit.getOfflinePlayers()) {
            if (offline.getName() != null && !offline.isOnline()) {
                names.add(offline.getName());
            }
        }
        return filter(input, names);
    }

    public static List<String> configKeys(String input, java.util.Collection<String> keys) {
        return filter(input, keys);
    }
}

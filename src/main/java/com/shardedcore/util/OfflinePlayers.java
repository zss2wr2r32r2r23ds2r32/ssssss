package com.shardedcore.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class OfflinePlayers {

    private OfflinePlayers() {
    }

    public static OfflinePlayer resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline.hasPlayedBefore() || offline.isOnline() ? offline : null;
    }

    public static String name(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player.getName() != null) {
            return player.getName();
        }
        return uuid.toString().substring(0, 8);
    }

    public static List<String> knownPlayers(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return TabCompleteHelper.filter(names, prefix);
    }

    public static List<String> onlinePlayers(String prefix) {
        return knownPlayers(prefix);
    }
}

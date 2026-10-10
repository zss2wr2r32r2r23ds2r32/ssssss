package com.shardedcore.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class Players {

    private Players() {
    }

    public static Player online(String name) {
        if (name == null) return null;
        return Bukkit.getPlayerExact(name);
    }

    public static OfflinePlayer offline(String name) {
        Player online = online(name);
        if (online != null) return online;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }
        return Bukkit.getOfflinePlayer(name);
    }

    public static String name(OfflinePlayer player) {
        if (player == null) return "Unknown";
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    public static UUID uuid(String name) {
        OfflinePlayer player = offline(name);
        return player == null ? null : player.getUniqueId();
    }
}

package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public final class OfflinePlayers {

    private OfflinePlayers() {
    }

    public static OfflinePlayer resolve(String name) {
        if (name == null || name.isBlank()) return null;
        OfflinePlayer online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached != null && cached.getName() != null) return cached;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && player.getName().equalsIgnoreCase(name)) return player;
        }
        return Bukkit.getOfflinePlayer(name);
    }

    public static String name(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }
}

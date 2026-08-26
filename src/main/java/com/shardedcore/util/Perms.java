package com.shardedcore.util;

import org.bukkit.entity.Player;

public final class Perms {

    private Perms() {
    }

    public static int highest(Player player, String prefix, int min, int max, int fallback) {
        if (player == null) return fallback;
        int highest = 0;
        for (int i = max; i >= min; i--) {
            if (player.hasPermission(prefix + i)) {
                highest = i;
                break;
            }
        }
        return highest == 0 ? fallback : Math.min(max, Math.max(min, highest));
    }
}

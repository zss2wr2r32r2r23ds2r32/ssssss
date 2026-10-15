package com.shardedcore.util;

import org.bukkit.entity.Player;

public final class PlaceholderUtil {

    private PlaceholderUtil() {
    }

    public static String apply(Player player, String input) {
        return Text.applyPlaceholders(input, player);
    }
}

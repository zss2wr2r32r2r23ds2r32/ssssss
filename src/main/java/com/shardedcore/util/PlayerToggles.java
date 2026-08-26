package com.shardedcore.util;

import com.shardedcore.ShardedCore;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class PlayerToggles {

    public static final String DEATH_MSG = "deathtoggle";
    public static final String JOIN_MSG = "jointoggle";

    private PlayerToggles() {}

    public static boolean deathMessages(Player player) {
        return getBool(player.getUniqueId(), DEATH_MSG, true);
    }

    public static void setDeathMessages(Player player, boolean enabled) {
        setBool(player.getUniqueId(), DEATH_MSG, enabled);
    }

    public static boolean joinMessages(Player player) {
        return getBool(player.getUniqueId(), JOIN_MSG, true);
    }

    public static void setJoinMessages(Player player, boolean enabled) {
        setBool(player.getUniqueId(), JOIN_MSG, enabled);
    }

    private static boolean getBool(UUID uuid, String key, boolean defaultValue) {
        ShardedCore plugin = ShardedCore.getInstance();
        if (plugin == null || plugin.stateStore() == null) return defaultValue;
        return plugin.stateStore().getBool(uuid, key, defaultValue);
    }

    private static void setBool(UUID uuid, String key, boolean value) {
        ShardedCore plugin = ShardedCore.getInstance();
        if (plugin == null || plugin.stateStore() == null) return;
        plugin.stateStore().setBool(uuid, key, value);
    }
}

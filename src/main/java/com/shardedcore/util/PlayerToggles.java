package com.shardedcore.util;

import com.shardedcore.ShardedCore;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class PlayerToggles {

    public static final String DEATH_MSG = "deathtoggle";
    public static final String JOIN_MSG = "jointoggle";
    public static final String MOB_SPAWN = "mobtoggle";
    public static final String SCOREBOARD = "scoreboard";
    public static final String SCOREBOARD_DISPLAY = "scoreboard-display";
    public static final String EVENT_SOUNDS = "eventsounds";

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

    public static boolean mobSpawn(Player player) {
        return getBool(player.getUniqueId(), MOB_SPAWN, true);
    }

    public static void setMobSpawn(Player player, boolean enabled) {
        setBool(player.getUniqueId(), MOB_SPAWN, enabled);
    }

    public static boolean scoreboard(Player player) {
        return getBool(player.getUniqueId(), SCOREBOARD, true);
    }

    public static void setScoreboard(Player player, boolean enabled) {
        setBool(player.getUniqueId(), SCOREBOARD, enabled);
    }

    public static boolean scoreboardDisplay(Player player) {
        return getBool(player.getUniqueId(), SCOREBOARD_DISPLAY, true);
    }

    public static void flipScoreboardDisplay(Player player) {
        setBool(player.getUniqueId(), SCOREBOARD_DISPLAY, !scoreboardDisplay(player));
    }

    public static boolean eventSounds(Player player) {
        return getBool(player.getUniqueId(), EVENT_SOUNDS, true);
    }

    public static void setEventSounds(Player player, boolean enabled) {
        setBool(player.getUniqueId(), EVENT_SOUNDS, enabled);
    }

    public static void noPermissionActionBar(Player player, String message) {
        player.sendActionBar(Text.cPlain(message));
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

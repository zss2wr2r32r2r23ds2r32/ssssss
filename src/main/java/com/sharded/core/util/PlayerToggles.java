package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

/** Per-player visibility toggles managed from /settings. */
public final class PlayerToggles {

    public static final String SCOREBOARD = "toggle-scoreboard";
    public static final String DEATH_MSG = "toggle-death-messages";
    public static final String JOIN_MSG = "toggle-join-messages";
    public static final String MOB_SPAWN = "toggle-mob-spawn";
    public static final String EVENT_SOUNDS = "toggle-event-sounds";

    private PlayerToggles() {
    }

    private static ShardedCore plugin() {
        return ShardedCore.get();
    }

    public static boolean scoreboard(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), SCOREBOARD, true);
    }

    /** Display state for settings GUI when using an external /sb command (e.g. TAB). */
    public static boolean scoreboardDisplay(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), SCOREBOARD, true);
    }

    public static void flipScoreboardDisplay(Player player) {
        plugin().stateStore().setBool(player.getUniqueId(), SCOREBOARD, !scoreboardDisplay(player));
    }

    public static void setScoreboard(Player player, boolean enabled) {
        plugin().stateStore().setBool(player.getUniqueId(), SCOREBOARD, enabled);
        if (enabled) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        } else {
            Scoreboard empty = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(empty);
        }
    }

    public static boolean deathMessages(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), DEATH_MSG, true);
    }

    public static void setDeathMessages(Player player, boolean enabled) {
        plugin().stateStore().setBool(player.getUniqueId(), DEATH_MSG, enabled);
    }

    public static boolean joinMessages(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), JOIN_MSG, true);
    }

    public static void setJoinMessages(Player player, boolean enabled) {
        plugin().stateStore().setBool(player.getUniqueId(), JOIN_MSG, enabled);
    }

    public static boolean mobSpawn(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), MOB_SPAWN, true);
    }

    public static void setMobSpawn(Player player, boolean enabled) {
        plugin().stateStore().setBool(player.getUniqueId(), MOB_SPAWN, enabled);
    }

    public static boolean eventSounds(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), EVENT_SOUNDS, true);
    }

    public static void setEventSounds(Player player, boolean enabled) {
        plugin().stateStore().setBool(player.getUniqueId(), EVENT_SOUNDS, enabled);
    }

    public static void noPermissionActionBar(Player player, String message) {
        player.sendActionBar(com.sharded.core.util.Text.c(message));
    }
}

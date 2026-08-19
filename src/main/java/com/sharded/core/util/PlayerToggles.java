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
    public static final String KILL_EFFECT = "killeffect-type";
    public static final String KILL_EFFECT_SHOW_OTHERS = "killeffect-show-others";
    public static final String SEE_KILL_EFFECTS = "toggle-see-killeffects";

    private PlayerToggles() {
    }

    private static ShardedCore plugin() {
        return ShardedCore.get();
    }

    public static boolean scoreboard(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), SCOREBOARD, true);
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

    public static String killEffect(Player player) {
        return plugin().stateStore().getString(player.getUniqueId(), KILL_EFFECT, "");
    }

    public static void setKillEffect(Player player, String effectId) {
        plugin().stateStore().setString(player.getUniqueId(), KILL_EFFECT, effectId == null ? "" : effectId);
    }

    public static boolean killEffectShowOthers(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), KILL_EFFECT_SHOW_OTHERS, true);
    }

    public static void setKillEffectShowOthers(Player player, boolean show) {
        plugin().stateStore().setBool(player.getUniqueId(), KILL_EFFECT_SHOW_OTHERS, show);
    }

    public static boolean seeKillEffects(Player player) {
        return plugin().stateStore().getBool(player.getUniqueId(), SEE_KILL_EFFECTS, true);
    }

    public static void setSeeKillEffects(Player player, boolean enabled) {
        plugin().stateStore().setBool(player.getUniqueId(), SEE_KILL_EFFECTS, enabled);
    }

    public static void noPermissionActionBar(Player player, String message) {
        player.sendActionBar(com.sharded.core.util.Text.c(message));
    }
}

package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

public final class PlayerToggles {
   public static final String SCOREBOARD = "toggle-scoreboard";
   public static final String DEATH_MSG = "toggle-death-messages";
   public static final String JOIN_MSG = "toggle-join-messages";
   public static final String EVENT_SOUNDS = "toggle-event-sounds";

   private PlayerToggles() {
   }

   private static ShardedCore plugin() {
      return ShardedCore.get();
   }

   public static boolean scoreboard(Player player) {
      return plugin().stateStore().getBool(player.getUniqueId(), "toggle-scoreboard", true);
   }

   public static boolean scoreboardDisplay(Player player) {
      return plugin().stateStore().getBool(player.getUniqueId(), "toggle-scoreboard", true);
   }

   public static void flipScoreboardDisplay(Player player) {
      plugin().stateStore().setBool(player.getUniqueId(), "toggle-scoreboard", !scoreboardDisplay(player));
   }

   public static void setScoreboard(Player player, boolean enabled) {
      plugin().stateStore().setBool(player.getUniqueId(), "toggle-scoreboard", enabled);
      if (enabled) {
         player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
      } else {
         Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
         player.setScoreboard(scoreboard);
      }
   }

   public static boolean deathMessages(Player player) {
      return plugin().stateStore().getBool(player.getUniqueId(), "toggle-death-messages", true);
   }

   public static void setDeathMessages(Player player, boolean enabled) {
      plugin().stateStore().setBool(player.getUniqueId(), "toggle-death-messages", enabled);
   }

   public static boolean joinMessages(Player player) {
      return plugin().stateStore().getBool(player.getUniqueId(), "toggle-join-messages", true);
   }

   public static void setJoinMessages(Player player, boolean enabled) {
      plugin().stateStore().setBool(player.getUniqueId(), "toggle-join-messages", enabled);
   }

   public static boolean eventSounds(Player player) {
      return plugin().stateStore().getBool(player.getUniqueId(), "toggle-event-sounds", true);
   }

   public static void setEventSounds(Player player, boolean enabled) {
      plugin().stateStore().setBool(player.getUniqueId(), "toggle-event-sounds", enabled);
   }

   public static void noPermissionActionBar(Player player, String message) {
      player.sendActionBar(Text.c(message));
   }
}

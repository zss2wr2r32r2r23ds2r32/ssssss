package com.sharded.core.modules.leaderboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.killstreaks.KillstreakDatabase;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.stats.StatsData;
import com.sharded.core.modules.stats.StatsModule;
import com.sharded.core.modules.teams.TeamDatabase;
import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.modules.tokens.TokenDatabase;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

final class LeaderboardService {
   private static final List<String> CACHED_TYPES = List.of(
         "tokens", "kills", "deaths", "killstreaks", "playtime", "teams", "totems", "duels");
   private final ShardedCore plugin;
   private final YamlConfiguration config;
   private final Map<String, List<LeaderboardService.Entry>> cache = new ConcurrentHashMap<>();
   private volatile long windowId = -1L;

   LeaderboardService(ShardedCore plugin, YamlConfiguration config) {
      this.plugin = plugin;
      this.config = config;
   }

   long intervalMillis() {
      return Math.max(1L, this.config.getLong("refresh-interval-seconds", 120L)) * 1000L;
   }

   long remainingMillis() {
      long interval = this.intervalMillis();
      long rem = interval - System.currentTimeMillis() % interval;
      return rem == interval ? 0L : rem;
   }

   boolean ensureFresh() {
      long window = System.currentTimeMillis() / this.intervalMillis();
      if (window == this.windowId && !this.cache.isEmpty()) {
         return false;
      }
      this.windowId = window;
      this.cache.clear();
      for (String type : CACHED_TYPES) {
         this.cache.put(type, List.copyOf(this.fetchLive(type)));
      }
      return true;
   }

   List<LeaderboardService.Entry> entries(String type) {
      this.ensureFresh();
      return this.cache.getOrDefault(this.cacheKey(type), List.of());
   }

   private String cacheKey(String type) {
      return switch (type.toLowerCase(Locale.ROOT)) {
         case "tokens", "token" -> "tokens";
         case "kills", "kill" -> "kills";
         case "deaths", "death" -> "deaths";
         case "playtime", "time" -> "playtime";
         case "killstreaks", "killstreak", "streak" -> "killstreaks";
         case "teams", "team" -> "teams";
         case "totems", "totem", "totempops", "totem_pops" -> "totems";
         case "duels", "duels_wins", "wins" -> "duels";
         default -> type.toLowerCase(Locale.ROOT);
      };
   }

   private List<LeaderboardService.Entry> fetchLive(String type) {
      return switch (type.toLowerCase(Locale.ROOT)) {
         case "tokens", "token" -> this.tokenEntries();
         case "kills", "kill" -> this.statEntries(Statistic.PLAYER_KILLS);
         case "deaths", "death" -> this.statEntries(Statistic.DEATHS);
         case "playtime", "time" -> this.statEntries(Statistic.PLAY_ONE_MINUTE);
         case "killstreaks", "killstreak", "streak" -> this.killstreakEntries();
         case "teams", "team" -> this.teamEntries();
         case "totems", "totem", "totempops", "totem_pops" -> this.totemEntries();
         case "duels", "duels_wins", "wins" -> this.duelsEntries("wins");
         default -> List.of();
      };
   }

   int rankOf(String type, UUID uuid) {
      if (uuid == null) {
         return -1;
      }
      if (isDuelsType(type)) {
         Player player = Bukkit.getPlayer(uuid);
         return player == null ? -1 : parseDuelsRank(player, duelsCategory(type));
      }
      if (this.isTeamType(type)) {
         Integer teamId = this.playerTeamId(uuid);
         return teamId == null ? -1 : this.teamRank(teamId);
      }
      List<LeaderboardService.Entry> list = this.entries(type);
      for (int i = 0; i < list.size(); i++) {
         if (list.get(i).uuid() != null && list.get(i).uuid().equals(uuid)) {
            return i + 1;
         }
      }
      return -1;
   }

   long valueOf(String type, UUID uuid) {
      if (uuid == null) {
         return 0L;
      }
      if (this.isTeamType(type)) {
         Integer teamId = this.playerTeamId(uuid);
         if (teamId == null) {
            return 0L;
         }
         LeaderboardService.Entry entry = this.teamEntry(teamId);
         return entry == null ? 0L : entry.value();
      }
      OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
      return switch (type.toLowerCase(Locale.ROOT)) {
         case "tokens", "token" -> {
            TokensModule tokens = this.plugin.modules().get(TokensModule.class);
            yield tokens != null && tokens.service() != null ? tokens.service().getBalance(uuid) : 0L;
         }
         case "kills", "kill" -> safeStat(player, Statistic.PLAYER_KILLS);
         case "deaths", "death" -> safeStat(player, Statistic.DEATHS);
         case "playtime", "time" -> Text.ticksToMinutes(safeStat(player, Statistic.PLAY_ONE_MINUTE));
         case "killstreaks", "killstreak", "streak" -> {
            KillstreaksModule streaks = this.plugin.modules().get(KillstreaksModule.class);
            yield streaks != null && streaks.database() != null ? streaks.database().getBest(uuid) : 0L;
         }
         case "totems", "totem", "totempops", "totem_pops" -> {
            StatsModule stats = this.plugin.modules().get(StatsModule.class);
            if (stats == null || stats.getStatsData() == null) {
               yield 0L;
            }
            var data = stats.getStatsData().getAll().get(uuid);
            yield data == null ? 0L : data.totems;
         }
         default -> 0L;
      };
   }

   private boolean isTeamType(String type) {
      String s = type.toLowerCase(Locale.ROOT);
      return s.equals("teams") || s.equals("team");
   }

   private Integer playerTeamId(UUID uuid) {
      TeamsModule teams = this.plugin.modules().get(TeamsModule.class);
      if (teams == null || teams.database() == null) {
         return null;
      }
      return teams.database().getTeamId(uuid);
   }

   int teamRank(int teamId) {
      List<LeaderboardService.Entry> list = this.entries("teams");

      for (int i = 0; i < list.size(); i++) {
         if (String.valueOf(teamId).equals(list.get(i).key())) {
            return i + 1;
         }
      }

      return -1;
   }

   LeaderboardService.Entry teamEntry(int teamId) {
      TeamsModule teamsmodule = this.plugin.modules().get(TeamsModule.class);
      if (teamsmodule == null) {
         return null;
      } else {
         TeamDatabase teamdatabase = teamsmodule.database();
         TeamDatabase.Team teamdatabase$team = teamdatabase.getTeamById(teamId);
         if (teamdatabase$team == null) {
            return null;
         } else {
            long i = this.teamScore(teamdatabase$team.id(), teamdatabase);
            return new LeaderboardService.Entry(String.valueOf(teamId), teamdatabase$team.name(), i, teamdatabase$team.leaderUuid());
         }
      }
   }

   private List<LeaderboardService.Entry> tokenEntries() {
      TokensModule tokensmodule = this.plugin.modules().get(TokensModule.class);
      if (tokensmodule == null) {
         return List.of();
      } else {
         TokenDatabase tokendatabase = tokensmodule.database();
         if (tokendatabase == null) {
            return List.of();
         } else {
            List<LeaderboardService.Entry> list = new ArrayList<>();

            for (TokenDatabase.LeaderEntry tokendatabase$leaderentry : tokendatabase.top(this.config.getInt("fetch-limit", 5000))) {
               list.add(
                  new LeaderboardService.Entry(
                     tokendatabase$leaderentry.uuid().toString(),
                     OfflinePlayers.name(tokendatabase$leaderentry.uuid()),
                     tokendatabase$leaderentry.value(),
                     tokendatabase$leaderentry.uuid()
                  )
               );
            }

            return list;
         }
      }
   }

   private List<LeaderboardService.Entry> totemEntries() {
      StatsModule statsModule = this.plugin.modules().get(StatsModule.class);
      if (statsModule == null || statsModule.getStatsData() == null) {
         return List.of();
      }
      List<LeaderboardService.Entry> list = new ArrayList<>();
      for (var entry : statsModule.getStatsData().getAll().entrySet()) {
         StatsData.PlayerStats stats = entry.getValue();
         if (stats.totems > 0) {
            String name = stats.name == null || stats.name.isBlank() ? OfflinePlayers.name(entry.getKey()) : stats.name;
            list.add(new LeaderboardService.Entry(entry.getKey().toString(), name, stats.totems, entry.getKey()));
         }
      }
      list.sort(Comparator.comparingLong(LeaderboardService.Entry::value).reversed());
      return list;
   }

   private List<LeaderboardService.Entry> killstreakEntries() {
      KillstreaksModule killstreaksmodule = this.plugin.modules().get(KillstreaksModule.class);
      if (killstreaksmodule != null && killstreaksmodule.database() != null) {
         List<LeaderboardService.Entry> list = new ArrayList<>();

         for (KillstreakDatabase.LeaderEntry killstreakdatabase$leaderentry : killstreaksmodule.database().topBest(this.config.getInt("fetch-limit", 5000))) {
            list.add(
               new LeaderboardService.Entry(
                  killstreakdatabase$leaderentry.uuid().toString(),
                  OfflinePlayers.name(killstreakdatabase$leaderentry.uuid()),
                  killstreakdatabase$leaderentry.value(),
                  killstreakdatabase$leaderentry.uuid()
               )
            );
         }

         return list;
      } else {
         return List.of();
      }
   }

   private List<LeaderboardService.Entry> statEntries(Statistic stat) {
      List<LeaderboardService.Entry> list = new ArrayList<>();

      for (OfflinePlayer offlineplayer : Bukkit.getOfflinePlayers()) {
         if (offlineplayer.getUniqueId() != null) {
            try {
               long i = (long)offlineplayer.getStatistic(stat);
               if (stat == Statistic.PLAY_ONE_MINUTE) {
                  i = Text.ticksToMinutes(i);
               }

               if (i > 0L) {
                  list.add(
                     new LeaderboardService.Entry(
                        offlineplayer.getUniqueId().toString(), OfflinePlayers.name(offlineplayer.getUniqueId()), i, offlineplayer.getUniqueId()
                     )
                  );
               }
            } catch (UnsupportedOperationException | IllegalStateException illegalstateexception) {
            }
         }
      }

      list.sort(Comparator.comparingLong(LeaderboardService.Entry::value).reversed());
      int j = this.config.getInt("fetch-limit", 5000);
      return list.size() <= j ? list : list.subList(0, j);
   }

   private List<LeaderboardService.Entry> teamEntries() {
      TeamsModule teamsmodule = this.plugin.modules().get(TeamsModule.class);
      if (teamsmodule == null) {
         return List.of();
      } else {
         TeamDatabase teamdatabase = teamsmodule.database();
         List<LeaderboardService.Entry> list = new ArrayList<>();

         for (TeamDatabase.Team teamdatabase$team : teamdatabase.listTeams()) {
            list.add(
               new LeaderboardService.Entry(
                  String.valueOf(teamdatabase$team.id()),
                  teamdatabase$team.name(),
                  this.teamScore(teamdatabase$team.id(), teamdatabase),
                  teamdatabase$team.leaderUuid()
               )
            );
         }

         list.sort(Comparator.comparingLong(LeaderboardService.Entry::value).reversed());
         return list;
      }
   }

   private long teamScore(int teamId, TeamDatabase db) {
      long i = this.config.getLong("teams.token-weight", 1L);
      long j = this.config.getLong("teams.kill-weight", 100L);
      long k = this.config.getLong("teams.playtime-hour-weight", 50L);
      long l = 0L;
      int i1 = 0;
      long j1 = 0L;
      TokensModule tokensmodule = this.plugin.modules().get(TokensModule.class);

      for (TeamDatabase.Member teamdatabase$member : db.getMembers(teamId)) {
         i1 += teamdatabase$member.kills();
         j1 += teamdatabase$member.playtimeMs();
         if (tokensmodule != null && tokensmodule.service() != null) {
            l += tokensmodule.service().getBalance(teamdatabase$member.uuid());
         }
      }

      long k1 = j1 / 3600000L;
      return l * i + (long)i1 * j + k1 * k;
   }

   private List<LeaderboardService.Entry> duelsEntries(String category) {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
         return List.of();
      } else {
         List<LeaderboardService.Entry> list = new ArrayList<>();

         for (int i = 1; i <= 10; i++) {
            String s = resolveGlobalPlaceholder("%duels-lb_top_" + category + "name" + i + "%");
            String s1 = resolveGlobalPlaceholder("%duels-lb_top_" + category + "value" + i + "%");
            if (!s.isBlank() && !s.contains("%") && !s.equalsIgnoreCase("none")) {
               long j = parseLong(s1);
               list.add(new LeaderboardService.Entry(String.valueOf(i), s, j, null));
            }
         }

         return list;
      }
   }

   private static boolean isDuelsType(String type) {
      String s = type.toLowerCase(Locale.ROOT);
      return s.equals("duels") || s.equals("duels_wins") || s.equals("wins");
   }

   private static String duelsCategory(String type) {
      return "wins";
   }

   private static int parseDuelsRank(Player player, String category) {
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
         return -1;
      } else {
         String s = PlaceholderAPI.setPlaceholders(player, "%duels-lb_rank_" + category + "%");
         return parseInt(s);
      }
   }

   private static String resolveGlobalPlaceholder(String placeholder) {
      return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null ? "" : PlaceholderAPI.setPlaceholders(null, placeholder);
   }

   private static long parseLong(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Long.parseLong(raw.replaceAll("[^0-9]", ""));
         } catch (NumberFormatException numberformatexception) {
            return 0L;
         }
      } else {
         return 0L;
      }
   }

   private static int parseInt(String raw) {
      if (raw != null && !raw.isBlank() && !raw.contains("%")) {
         try {
            return Integer.parseInt(raw.replaceAll("[^0-9]", ""));
         } catch (NumberFormatException numberformatexception) {
            return -1;
         }
      } else {
         return -1;
      }
   }

   String formatValue(String type, long value) {
      String s = type.toLowerCase();

      return switch (s) {
         case "playtime", "time" -> Text.formatPlaytime(value);
         default -> String.valueOf(value);
      };
   }

   LeaderboardService.StatsSnapshot statsFor(UUID uuid) {
      OfflinePlayer offlineplayer = Bukkit.getOfflinePlayer(uuid);
      long i = safeStat(offlineplayer, Statistic.PLAYER_KILLS);
      long j = safeStat(offlineplayer, Statistic.DEATHS);
      long k = Text.ticksToMinutes(safeStat(offlineplayer, Statistic.PLAY_ONE_MINUTE));
      long l = 0L;
      TokensModule tokensmodule = this.plugin.modules().get(TokensModule.class);
      if (tokensmodule != null && tokensmodule.service() != null) {
         l = tokensmodule.service().getBalance(uuid);
      }

      int i1 = 0;
      KillstreaksModule killstreaksmodule = this.plugin.modules().get(KillstreaksModule.class);
      if (killstreaksmodule != null && killstreaksmodule.database() != null) {
         i1 = killstreaksmodule.database().getBest(uuid);
      }

      String s = this.config.getString("stats.no-team", "None");
      TeamsModule teamsmodule = this.plugin.modules().get(TeamsModule.class);
      if (teamsmodule != null && teamsmodule.database() != null) {
         Integer integer = teamsmodule.database().getTeamId(uuid);
         if (integer != null) {
            TeamDatabase.Team teamdatabase$team = teamsmodule.database().getTeamById(integer);
            if (teamdatabase$team != null) {
               s = teamdatabase$team.name();
            }
         }
      }

      String s1 = this.plugin.luckPerms().prefix(uuid);
      return new LeaderboardService.StatsSnapshot(OfflinePlayers.name(uuid), s1, i, j, k, l, i1, s);
   }

   private static long safeStat(OfflinePlayer player, Statistic stat) {
      try {
         return (long)player.getStatistic(stat);
      } catch (Exception exception) {
         return 0L;
      }
   }

   static record Entry(String key, String displayName, long value, UUID uuid) {
   }

   static record StatsSnapshot(String name, String prefix, long kills, long deaths, long playMinutes, long tokens, int bestStreak, String team) {
   }
}

package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.duel.DuelModule;
import com.sharded.core.modules.killstreaks.KillstreakDatabase;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.tokens.TokenDatabase;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.modules.tokens.TokensModule;
import com.sharded.core.util.PlaceholderUtil;
import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

final class StatFetcher {
   private final ShardedCore plugin;
   private final YamlConfiguration config;

   StatFetcher(ShardedCore plugin, YamlConfiguration config) {
      this.plugin = plugin;
      this.config = config;
   }

   List<CachedEntry> fetch(String statistic, int limit) {
      return this.fetch(statistic, limit, List.of());
   }

   List<CachedEntry> fetch(String statistic, int limit, List<CachedEntry> online) {
      String s = statistic.toLowerCase(Locale.ROOT);
      int i = Math.max(1, Math.min(limit, this.config.getInt("fetch-limit", 5000)));
      List<CachedEntry> list = online == null ? List.of() : online;

      return switch (s) {
         case "tokens", "token" -> this.tokens(i, list);
         case "killstreaks", "killstreak", "streaks", "streak" -> this.killstreaks(i);
         case "elo", "rating" -> this.elo(i);
         case "duels", "wins", "duelwins" -> this.wins(i);
         case "kills" -> this.bukkit(Statistic.PLAYER_KILLS, i, "kills", list);
         case "deaths" -> this.bukkit(Statistic.DEATHS, i, "deaths", list);
         case "playtime", "time" -> this.bukkit(Statistic.PLAY_ONE_MINUTE, i, "playtime", list);
         default -> this.custom(s, i);
      };
   }

   Map<String, List<CachedEntry>> snapshotOnline(Collection<String> keys) {
      Map<String, List<CachedEntry>> map = new HashMap<>();
      if (keys == null) {
         return map;
      } else {
         for (String s : keys) {
            String s1 = s.toLowerCase(Locale.ROOT);
            List<CachedEntry> list = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
               if (player != null && player.getUniqueId() != null) {
                  long i = this.liveValue(s1, player);
                  if (i > 0L) {
                     list.add(new CachedEntry(0, player.getUniqueId(), displayName(player.getName()), i, this.format(s1, i)));
                  }
               }
            }

            map.put(s1, list);
         }

         return map;
      }
   }

   private long liveValue(String key, Player player) {
      try {
         return switch (key) {
            case "kills" -> (long)player.getStatistic(Statistic.PLAYER_KILLS);
            case "deaths" -> (long)player.getStatistic(Statistic.DEATHS);
            case "playtime", "time" -> (long)player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            case "tokens", "token" -> {
               TokenService tokenservice = this.plugin.modules() == null ? null : this.plugin.modules().tokens();
               yield tokenservice == null ? 0L : tokenservice.getBalance(player.getUniqueId());
            }
            default -> 0L;
         };
      } catch (Exception exception) {
         return 0L;
      }
   }

   String format(String statistic, long value) {
      String s = statistic.toLowerCase(Locale.ROOT);
      String s1 = this.config.getString("statistics." + s + ".format", s.equals("playtime") ? "playtime" : "number");
      return "playtime".equalsIgnoreCase(s1) ? Text.formatPlaytime(Text.ticksToMinutes(value)) : String.format(Locale.US, "%,d", value);
   }

   private List<CachedEntry> tokens(int limit, List<CachedEntry> online) {
      List<CachedEntry> list = new ArrayList<>();
      TokensModule tokensmodule = this.plugin.modules().get(TokensModule.class);
      if (tokensmodule != null && tokensmodule.database() != null) {
         int i = Math.max(limit, this.config.getInt("fetch-limit", 5000));

         for (TokenDatabase.LeaderEntry tokendatabase$leaderentry : tokensmodule.database().top(i)) {
            list.add(this.row(0, tokendatabase$leaderentry.uuid(), tokendatabase$leaderentry.value(), "tokens"));
         }
      }

      return RankMerge.takeTop(online, list, limit);
   }

   private List<CachedEntry> killstreaks(int limit) {
      KillstreaksModule killstreaksmodule = this.plugin.modules().get(KillstreaksModule.class);
      if (killstreaksmodule != null && killstreaksmodule.database() != null) {
         List<CachedEntry> list = new ArrayList<>();
         int i = 1;

         for (KillstreakDatabase.LeaderEntry killstreakdatabase$leaderentry : killstreaksmodule.database().topBest(limit)) {
            list.add(this.row(i++, killstreakdatabase$leaderentry.uuid(), killstreakdatabase$leaderentry.value(), "killstreaks"));
         }

         return list;
      } else {
         return List.of();
      }
   }

   private List<CachedEntry> elo(int limit) {
      DuelModule duelmodule = this.plugin.modules().get(DuelModule.class);
      if (duelmodule == null) {
         return List.of();
      } else {
         List<CachedEntry> list = new ArrayList<>();
         int i = 1;

         for (DuelModule.DuelEloEntry duelmodule$dueleloentry : duelmodule.topElo(limit)) {
            list.add(
               new CachedEntry(
                  i,
                  duelmodule$dueleloentry.uuid(),
                  displayName(duelmodule$dueleloentry.name()),
                  (long)duelmodule$dueleloentry.elo(),
                  this.format("elo", (long)duelmodule$dueleloentry.elo())
               )
            );
            i++;
         }

         return list;
      }
   }

   private List<CachedEntry> wins(int limit) {
      DuelModule duelmodule = this.plugin.modules().get(DuelModule.class);
      if (duelmodule == null) {
         return List.of();
      } else {
         List<CachedEntry> list = new ArrayList<>();
         int i = 1;

         for (DuelModule.DuelWinEntry duelmodule$duelwinentry : duelmodule.topWins(limit)) {
            list.add(
               new CachedEntry(
                  i,
                  duelmodule$duelwinentry.uuid(),
                  displayName(duelmodule$duelwinentry.name()),
                  (long)duelmodule$duelwinentry.wins(),
                  this.format("duels", (long)duelmodule$duelwinentry.wins())
               )
            );
            i++;
         }

         return list;
      }
   }

   private List<CachedEntry> bukkit(Statistic statistic, int limit, String key, List<CachedEntry> online) {
      List<CachedEntry> list = new ArrayList<>();
      Set<UUID> set = new HashSet<>();

      List<CachedEntry> onlineEntries = online == null ? List.of() : online;
      for (CachedEntry cachedentry : onlineEntries) {
         if (cachedentry != null && cachedentry.uuid() != null) {
            set.add(cachedentry.uuid());
         }
      }

      int j = Math.max(limit, this.config.getInt("fetch-limit", 5000));

      for (OfflinePlayer offlineplayer : Bukkit.getOfflinePlayers()) {
         if (offlineplayer != null
            && offlineplayer.getUniqueId() != null
            && !set.contains(offlineplayer.getUniqueId())
            && (offlineplayer.getPlayer() != null || offlineplayer.hasPlayedBefore())) {
            long i;
            try {
               i = (long)offlineplayer.getStatistic(statistic);
            } catch (Exception exception) {
               continue;
            }

            if (i > 0L) {
               list.add(new CachedEntry(0, offlineplayer.getUniqueId(), displayName(offlineplayer.getName()), i, this.format(key, i)));
               if (list.size() >= j) {
                  break;
               }
            }
         }
      }

      return RankMerge.takeTop(online, list, limit);
   }

   private List<CachedEntry> custom(String key, int limit) {
      String s = this.config.getString("statistics." + key + ".placeholder", "");
      if (s.isBlank() && key.startsWith("papi:")) {
         s = "%" + key.substring(5) + "%";
      } else if (s.isBlank() && key.startsWith("placeholder:")) {
         s = key.substring("placeholder:".length());
         if (!s.startsWith("%")) {
            s = "%" + s + "%";
         }
      }

      if (s.isBlank()) {
         Statistic statistic = statisticNamed(key);
         return statistic != null ? this.bukkit(statistic, limit, key, List.of()) : List.of();
      } else {
         List<CachedEntry> list = new ArrayList<>();

         for (Player player : Bukkit.getOnlinePlayers()) {
            String s1 = PlaceholderUtil.apply(player, s);
            long i = parseLong(s1);
            if (i > 0L) {
               list.add(new CachedEntry(0, player.getUniqueId(), displayName(player.getName()), i, this.format(key, i)));
            }
         }

         list.sort(Comparator.comparingLong(CachedEntry::value).reversed());
         List<CachedEntry> list1 = new ArrayList<>();
         int j = 1;

         for (CachedEntry cachedentry : list) {
            if (j > limit) {
               break;
            }

            list1.add(new CachedEntry(j, cachedentry.uuid(), cachedentry.name(), cachedentry.value(), cachedentry.formatted()));
            j++;
         }

         return list1;
      }
   }

   private CachedEntry row(int rank, UUID uuid, long value, String statistic) {
      OfflinePlayer offlineplayer = Bukkit.getOfflinePlayer(uuid);
      return new CachedEntry(rank, uuid, displayName(offlineplayer.getName()), value, this.format(statistic, value));
   }

   static String displayName(String raw) {
      if (raw != null && !raw.isBlank()) {
         return raw.chars().anyMatch(Character::isLowerCase) ? raw : Character.toUpperCase(raw.charAt(0)) + raw.substring(1).toLowerCase(Locale.ROOT);
      } else {
         return "Unknown";
      }
   }

   static Statistic statisticNamed(String key) {
      try {
         return Statistic.valueOf(key.toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException illegalargumentexception) {
         return null;
      }
   }

   static long parseLong(String raw) {
      if (raw == null) {
         return 0L;
      } else {
         String s = raw.replace(",", "").replace("_", "").trim();
         int i = 0;

         while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '-')) {
            i++;
         }

         if (i == 0) {
            return 0L;
         } else {
            try {
               return Long.parseLong(s.substring(0, i));
            } catch (NumberFormatException numberformatexception) {
               return 0L;
            }
         }
      }
   }
}

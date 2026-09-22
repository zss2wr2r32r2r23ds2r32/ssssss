package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.api.leaderboard.LeaderboardEntry;
import com.sharded.core.api.leaderboard.LeaderboardUpdateEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

final class RankingCache {
   private final ShardedCore plugin;
   private final YamlConfiguration config;
   private final StatFetcher fetcher;
   private final Map<String, List<CachedEntry>> ranks = new ConcurrentHashMap<>();
   private final Map<String, Long> fetchedAt = new ConcurrentHashMap<>();
   private volatile boolean running;
   private volatile long lastRefreshMs = System.currentTimeMillis();
   private BukkitTask task;
   private Consumer<String> onUpdate = statistic -> {
   };

   RankingCache(ShardedCore plugin, YamlConfiguration config) {
      this.plugin = plugin;
      this.config = config;
      this.fetcher = new StatFetcher(plugin, config);
   }

   void onUpdate(Consumer<String> onUpdate) {
      this.onUpdate = onUpdate;
   }

   void start() {
      this.stop();
      this.lastRefreshMs = System.currentTimeMillis();
      long i = Math.max(5L, this.config.getLong("refresh-seconds", 120L));
      this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::refreshAll, 40L, i * 20L);
   }

   void stop() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }
   }

   void refreshAll() {
      this.refresh(List.copyOf(this.ranks.keySet()));
   }

   void ensure(String statistic) {
      this.ranks.computeIfAbsent(statistic.toLowerCase(Locale.ROOT), key -> new ArrayList<>());
   }

   void refresh(String statistic) {
      this.refresh(List.of(statistic.toLowerCase(Locale.ROOT)));
   }

   void refresh(List<String> statistics) {
      if (!statistics.isEmpty() && !this.running) {
         this.running = true;
         int i = Math.max(1, this.config.getInt("cache-size", 100));
         List<String> list = new ArrayList<>();

         for (String s : statistics) {
            list.add(s.toLowerCase(Locale.ROOT));
         }

         Map<String, List<CachedEntry>> map;
         try {
            map = this.fetcher.snapshotOnline(list);
         } catch (Exception exception) {
            this.plugin.getLogger().warning("[leaderboards] Could not snapshot online stats: " + exception.getMessage());
            map = Map.of();
         }

         Map<String, List<CachedEntry>> map1 = map;
         Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            Map<String, List<CachedEntry>> map2 = new ConcurrentHashMap<>();

            for (String s1 : list) {
               try {
                  map2.put(s1, this.fetcher.fetch(s1, i, map1.getOrDefault(s1, List.of())));
               } catch (Exception exception1) {
                  this.plugin.getLogger().warning("[leaderboards] Could not fetch " + s1 + ": " + exception1.getMessage());
                  map2.put(s1, this.ranks.getOrDefault(s1, List.of()));
               }
            }

            Bukkit.getScheduler().runTask(this.plugin, () -> {
               this.running = false;
               long j = System.currentTimeMillis();
               this.lastRefreshMs = j;

               for (Entry<String, List<CachedEntry>> entry : map2.entrySet()) {
                  List<CachedEntry> list1 = this.ranks.getOrDefault(entry.getKey(), List.of());
                  this.ranks.put(entry.getKey(), List.copyOf(entry.getValue()));
                  this.fetchedAt.put(entry.getKey(), j);
                  List<UUID> list2 = uuids(list1);
                  List<UUID> list3 = uuids(entry.getValue());
                  if (!list2.equals(list3) || valuesChanged(list1, entry.getValue())) {
                     Bukkit.getPluginManager().callEvent(new LeaderboardUpdateEvent(entry.getKey(), list2, list3));
                  }

                  this.onUpdate.accept(entry.getKey());
               }
            });
         });
      }
   }

   CachedEntry entry(String statistic, int position) {
      String s = this.config.getString("empty-name", "---");
      String s1 = this.config.getString("empty-value", "---");
      List<CachedEntry> list = this.ranks.getOrDefault(statistic.toLowerCase(Locale.ROOT), List.of());
      return position >= 1 && position <= list.size() ? list.get(position - 1) : CachedEntry.empty(Math.max(1, position), s, s1);
   }

   List<CachedEntry> entries(String statistic, int size) {
      List<CachedEntry> list = new ArrayList<>();

      for (int i = 1; i <= size; i++) {
         list.add(this.entry(statistic, i));
      }

      return list;
   }

   String field(String statistic, String field, int position) {
      CachedEntry cachedentry = this.entry(statistic, position);

      return switch (field) {
         case "name" -> cachedentry.getPlayerName();
         case "value" -> cachedentry.getFormattedValue();
         case "uuid" -> cachedentry.getPlayerUUID() == null ? "" : cachedentry.getPlayerUUID().toString();
         case "head" -> cachedentry.getPlayerUUID() == null ? "" : cachedentry.getPlayerUUID().toString();
         default -> "";
      };
   }

   int rankOf(String statistic, UUID uuid) {
      if (uuid == null) {
         return -1;
      } else {
         List<CachedEntry> list = this.ranks.getOrDefault(statistic.toLowerCase(Locale.ROOT), List.of());

         for (int i = 0; i < list.size(); i++) {
            if (uuid.equals(list.get(i).uuid())) {
               return i + 1;
            }
         }

         return -1;
      }
   }

   CachedEntry ofPlayer(String statistic, UUID uuid) {
      if (uuid == null) {
         return null;
      } else {
         for (CachedEntry cachedentry : this.ranks.getOrDefault(statistic.toLowerCase(Locale.ROOT), List.of())) {
            if (uuid.equals(cachedentry.uuid())) {
               return cachedentry;
            }
         }

         return null;
      }
   }

   LeaderboardEntry apiEntry(String statistic, int position) {
      return this.entry(statistic, position);
   }

   private static List<UUID> uuids(List<CachedEntry> rows) {
      List<UUID> list = new ArrayList<>();

      for (CachedEntry cachedentry : rows) {
         list.add(cachedentry.uuid());
      }

      return list;
   }

   long millisUntilRefresh() {
      long interval = Math.max(5L, this.config.getLong("refresh-seconds", 120L)) * 1000L;
      return Math.max(0L, this.lastRefreshMs + interval - System.currentTimeMillis());
   }

   private static boolean valuesChanged(List<CachedEntry> previous, List<CachedEntry> next) {
      if (previous.size() != next.size()) {
         return true;
      } else {
         for (int i = 0; i < previous.size(); i++) {
            if (previous.get(i).value() != next.get(i).value()) {
               return true;
            }
         }

         return false;
      }
   }
}

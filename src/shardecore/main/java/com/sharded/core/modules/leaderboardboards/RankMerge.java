package com.sharded.core.modules.leaderboardboards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class RankMerge {
   private RankMerge() {
   }

   static List<CachedEntry> takeTop(List<CachedEntry> online, List<CachedEntry> stored, int limit) {
      Map<UUID, CachedEntry> map = new LinkedHashMap<>();
      addAll(map, stored);
      addAll(map, online);
      List<CachedEntry> list = new ArrayList<>();

      for (CachedEntry cachedentry : map.values()) {
         if (cachedentry != null && cachedentry.uuid() != null && cachedentry.value() > 0L) {
            list.add(cachedentry);
         }
      }

      list.sort(
         Comparator.comparingLong(CachedEntry::value)
            .reversed()
            .thenComparing(entry -> entry.name() == null ? "" : entry.name(), String.CASE_INSENSITIVE_ORDER)
      );
      List<CachedEntry> list1 = new ArrayList<>();
      int j = 1;
      int i = Math.max(1, limit);

      for (CachedEntry cachedentry1 : list) {
         if (j > i) {
            break;
         }

         list1.add(new CachedEntry(j, cachedentry1.uuid(), cachedentry1.name(), cachedentry1.value(), cachedentry1.formatted()));
         j++;
      }

      return list1;
   }

   private static void addAll(Map<UUID, CachedEntry> byId, List<CachedEntry> rows) {
      if (rows != null) {
         for (CachedEntry cachedentry : rows) {
            if (cachedentry != null && cachedentry.uuid() != null) {
               byId.put(cachedentry.uuid(), cachedentry);
            }
         }
      }
   }
}

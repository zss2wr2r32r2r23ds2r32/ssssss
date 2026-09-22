package com.sharded.core.api.leaderboard;

import java.util.Collection;

public interface LeaderboardManager {
   Leaderboard getLeaderboard(String var1);

   Collection<Leaderboard> getLeaderboards();

   void refresh(String var1);

   void refreshAll();

   String placeholder(String var1);
}

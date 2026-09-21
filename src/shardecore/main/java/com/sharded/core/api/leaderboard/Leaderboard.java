package com.sharded.core.api.leaderboard;

import java.util.List;
import org.bukkit.Location;

public interface Leaderboard {
   String getId();

   String getStatistic();

   int getSize();

   LeaderboardEntry getEntry(int var1);

   List<LeaderboardEntry> getEntries();

   Location getLocation();

   boolean isHologramEnabled();
}

package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.api.leaderboard.Leaderboard;
import com.sharded.core.api.leaderboard.LeaderboardEntry;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

final class BoardView implements Leaderboard {
   private final BoardDefinition board;
   private final RankingCache cache;

   BoardView(BoardDefinition board, RankingCache cache) {
      this.board = board;
      this.cache = cache;
   }

   @Override
   public String getId() {
      return this.board.id;
   }

   @Override
   public String getStatistic() {
      return this.board.statisticKey();
   }

   @Override
   public int getSize() {
      return this.board.entries;
   }

   @Override
   public LeaderboardEntry getEntry(int position) {
      return this.cache.apiEntry(this.board.statisticKey(), position);
   }

   @Override
   public List<LeaderboardEntry> getEntries() {
      return new ArrayList<>(this.cache.entries(this.board.statisticKey(), this.board.entries));
   }

   @Override
   public Location getLocation() {
      return this.board.location();
   }

   @Override
   public boolean isHologramEnabled() {
      return this.board.hologramEnabled && this.board.placed();
   }
}

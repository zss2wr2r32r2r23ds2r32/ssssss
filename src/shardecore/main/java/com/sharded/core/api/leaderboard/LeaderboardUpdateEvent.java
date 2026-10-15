package com.sharded.core.api.leaderboard;

import java.util.List;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class LeaderboardUpdateEvent extends Event {
   private static final HandlerList HANDLERS = new HandlerList();
   private final String statistic;
   private final List<UUID> previous;
   private final List<UUID> current;

   public LeaderboardUpdateEvent(String statistic, List<UUID> previous, List<UUID> current) {
      super(false);
      this.statistic = statistic;
      this.previous = List.copyOf(previous);
      this.current = List.copyOf(current);
   }

   public String getStatistic() {
      return this.statistic;
   }

   public List<UUID> getPreviousUuids() {
      return this.previous;
   }

   public List<UUID> getCurrentUuids() {
      return this.current;
   }

   public boolean positionsChanged() {
      return !this.previous.equals(this.current);
   }

   public HandlerList getHandlers() {
      return HANDLERS;
   }

   public static HandlerList getHandlerList() {
      return HANDLERS;
   }
}

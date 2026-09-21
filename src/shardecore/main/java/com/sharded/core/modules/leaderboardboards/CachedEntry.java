package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.api.leaderboard.LeaderboardEntry;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

record CachedEntry(int position, UUID uuid, String name, long value, String formatted) implements LeaderboardEntry {
   static CachedEntry empty(int position, String emptyName, String emptyValue) {
      return new CachedEntry(position, null, emptyName, 0L, emptyValue);
   }

   @Override
   public int getPosition() {
      return this.position;
   }

   @Override
   public UUID getPlayerUUID() {
      return this.uuid;
   }

   @Override
   public String getPlayerName() {
      return this.name;
   }

   @Override
   public long getValue() {
      return this.value;
   }

   @Override
   public String getFormattedValue() {
      return this.formatted;
   }

   @Override
   public ItemStack getHead() {
      return this.uuid == null ? PlayerHeads.empty() : PlayerHeads.of(this.uuid, this.name);
   }
}

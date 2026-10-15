package com.sharded.core.api.leaderboard;

import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public interface LeaderboardEntry {
   int getPosition();

   UUID getPlayerUUID();

   String getPlayerName();

   long getValue();

   String getFormattedValue();

   ItemStack getHead();
}

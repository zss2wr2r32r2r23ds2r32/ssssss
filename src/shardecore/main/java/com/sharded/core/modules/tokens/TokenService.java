package com.sharded.core.modules.tokens;

import com.sharded.core.util.OfflinePlayers;
import java.util.UUID;
import org.bukkit.OfflinePlayer;

public final class TokenService {
   private final TokenDatabase database;

   public TokenService(TokenDatabase database) {
      this.database = database;
   }

   public long getBalance(UUID uuid) {
      return this.database.getBalance(uuid);
   }

   public void setBalance(UUID uuid, long amount) {
      this.database.setBalance(uuid, amount);
   }

   public void give(UUID uuid, long amount) {
      if (amount > 0L) {
         this.database.setBalance(uuid, this.getBalance(uuid) + amount);
      }
   }

   public boolean take(UUID uuid, long amount) {
      if (amount <= 0L) {
         return true;
      } else {
         long i = this.getBalance(uuid);
         if (i < amount) {
            return false;
         } else {
            this.database.setBalance(uuid, i - amount);
            return true;
         }
      }
   }

   public void reset(UUID uuid) {
      this.database.setBalance(uuid, 0L);
   }

   public OfflinePlayer resolve(String name) {
      return OfflinePlayers.resolve(name);
   }
}

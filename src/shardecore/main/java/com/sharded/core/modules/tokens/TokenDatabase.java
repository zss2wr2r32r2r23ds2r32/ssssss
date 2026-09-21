package com.sharded.core.modules.tokens;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TokenDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public TokenDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file1 = new File(folder, "tokens.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

      try (Statement statement = this.connection.createStatement()) {
         statement.execute(
            "CREATE TABLE IF NOT EXISTS tokens (\n    uuid TEXT PRIMARY KEY,\n    balance INTEGER NOT NULL DEFAULT 0,\n    updated_at INTEGER NOT NULL\n)\n"
         );
      }
   }

   public synchronized long getBalance(UUID uuid) {
      try {
         long i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT balance FROM tokens WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               i = resultset.next() ? resultset.getLong("balance") : 0L;
            }
         }

         return i;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().severe("Failed to read tokens: " + sqlexception.getMessage());
         return 0L;
      }
   }

   public synchronized void setBalance(UUID uuid, long balance) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO tokens (uuid, balance, updated_at) VALUES (?, ?, ?)\nON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance, updated_at = excluded.updated_at\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setLong(2, Math.max(0L, balance));
         preparedstatement.setLong(3, System.currentTimeMillis());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().severe("Failed to save tokens: " + sqlexception.getMessage());
      }
   }

   public synchronized List<TokenDatabase.LeaderEntry> top(int limit) {
      List<TokenDatabase.LeaderEntry> list = new ArrayList<>();

      try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT uuid, balance FROM tokens ORDER BY balance DESC LIMIT ?")) {
         preparedstatement.setInt(1, limit);

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               list.add(new TokenDatabase.LeaderEntry(UUID.fromString(resultset.getString("uuid")), resultset.getLong("balance")));
            }
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().severe("Failed to read token leaderboard: " + sqlexception.getMessage());
      }

      return list;
   }

   public synchronized void close() {
      try {
         if (this.connection != null && !this.connection.isClosed()) {
            this.connection.close();
         }
      } catch (SQLException sqlexception) {
      }

      this.connection = null;
   }

   public static record LeaderEntry(UUID uuid, long value) {
   }
}

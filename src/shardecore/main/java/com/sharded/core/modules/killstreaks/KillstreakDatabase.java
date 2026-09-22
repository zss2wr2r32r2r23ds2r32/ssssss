package com.sharded.core.modules.killstreaks;

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

public final class KillstreakDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public KillstreakDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file1 = new File(folder, "killstreaks.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

      try (Statement statement = this.connection.createStatement()) {
         statement.execute(
            "CREATE TABLE IF NOT EXISTS killstreaks (\n    uuid TEXT PRIMARY KEY,\n    current_streak INTEGER NOT NULL DEFAULT 0,\n    best_streak INTEGER NOT NULL DEFAULT 0\n)\n"
         );
      }
   }

   public synchronized int getCurrent(UUID uuid) {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT current_streak FROM killstreaks WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               i = resultset.next() ? resultset.getInt("current_streak") : 0;
            }
         }

         return i;
      } catch (SQLException sqlexception) {
         return 0;
      }
   }

   public synchronized int getBest(UUID uuid) {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT best_streak FROM killstreaks WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               i = resultset.next() ? resultset.getInt("best_streak") : 0;
            }
         }

         return i;
      } catch (SQLException sqlexception) {
         return 0;
      }
   }

   public synchronized void setStreak(UUID uuid, int current) {
      int i = Math.max(this.getBest(uuid), current);

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO killstreaks (uuid, current_streak, best_streak) VALUES (?, ?, ?)\nON CONFLICT(uuid) DO UPDATE SET current_streak = excluded.current_streak, best_streak = excluded.best_streak\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setInt(2, current);
         preparedstatement.setInt(3, i);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().severe("Failed to save killstreak: " + sqlexception.getMessage());
      }
   }

   public synchronized List<KillstreakDatabase.LeaderEntry> topBest(int limit) {
      List<KillstreakDatabase.LeaderEntry> list = new ArrayList<>();

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("SELECT uuid, best_streak FROM killstreaks ORDER BY best_streak DESC LIMIT ?")) {
         preparedstatement.setInt(1, limit);

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               list.add(new KillstreakDatabase.LeaderEntry(UUID.fromString(resultset.getString("uuid")), (long)resultset.getInt("best_streak")));
            }
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().severe("Failed to read killstreak leaderboard: " + sqlexception.getMessage());
      }

      return list;
   }

   public synchronized void reset(UUID uuid) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO killstreaks (uuid, current_streak, best_streak) VALUES (?, 0, 0)\nON CONFLICT(uuid) DO UPDATE SET current_streak = 0, best_streak = 0\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to reset killstreak: " + sqlexception.getMessage());
      }
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

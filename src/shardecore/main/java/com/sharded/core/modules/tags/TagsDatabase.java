package com.sharded.core.modules.tags;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class TagsDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public TagsDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      if (!folder.exists() && !folder.mkdirs()) {
         throw new SQLException("Could not create tags folder: " + folder.getAbsolutePath());
      }
      File file = new File(folder, "tags.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
      try (Statement statement = this.connection.createStatement()) {
         statement.execute(
            "CREATE TABLE IF NOT EXISTS tags_owned (uuid TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY (uuid, tag_id))"
         );
      }
   }

   public synchronized void unlock(UUID uuid, String tagId) {
      try (PreparedStatement statement = this.connection.prepareStatement("INSERT OR IGNORE INTO tags_owned (uuid, tag_id) VALUES (?, ?)")) {
         statement.setString(1, uuid.toString());
         statement.setString(2, tagId.toLowerCase());
         statement.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("Failed to unlock tag: " + ex.getMessage());
      }
   }

   public synchronized boolean isUnlocked(UUID uuid, String tagId) {
      try (PreparedStatement statement = this.connection.prepareStatement("SELECT 1 FROM tags_owned WHERE uuid = ? AND tag_id = ?")) {
         statement.setString(1, uuid.toString());
         statement.setString(2, tagId.toLowerCase());
         try (ResultSet result = statement.executeQuery()) {
            return result.next();
         }
      } catch (SQLException ex) {
         return false;
      }
   }

   public synchronized void close() {
      try {
         if (this.connection != null && !this.connection.isClosed()) {
            this.connection.close();
         }
      } catch (SQLException ignored) {
      }
      this.connection = null;
   }
}

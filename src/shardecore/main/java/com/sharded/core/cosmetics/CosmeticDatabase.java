package com.sharded.core.cosmetics;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CosmeticDatabase {
   private final ShardedCore plugin;
   private Connection connection;
   private final ConcurrentHashMap<UUID, CosmeticDatabase.PlayerCosmetics> cache = new ConcurrentHashMap<>();

   public CosmeticDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      if (!folder.exists() && !folder.mkdirs()) {
         throw new SQLException("Could not create cosmetics folder: " + folder.getAbsolutePath());
      } else {
         File file1 = new File(folder, "cosmetics.db");
         this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

         try (Statement statement = this.connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute(
               "CREATE TABLE IF NOT EXISTS player_cosmetics (\n    uuid TEXT PRIMARY KEY,\n    tag_id TEXT,\n    tag_display TEXT,\n    name_color TEXT,\n    chat_color TEXT\n)\n"
            );
         }
      }
   }

   public CosmeticDatabase.PlayerCosmetics get(UUID uuid) {
      CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics = this.cache.get(uuid);
      if (cosmeticdatabase$playercosmetics != null) {
         return cosmeticdatabase$playercosmetics;
      } else {
         CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics1 = this.load(uuid);
         this.cache.put(uuid, cosmeticdatabase$playercosmetics1);
         return cosmeticdatabase$playercosmetics1;
      }
   }

   private synchronized CosmeticDatabase.PlayerCosmetics load(UUID uuid) {
      try {
         CosmeticDatabase.PlayerCosmetics cosmeticdatabase$playercosmetics;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT tag_id, tag_display, name_color, chat_color FROM player_cosmetics WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return CosmeticDatabase.PlayerCosmetics.empty();
               }

               cosmeticdatabase$playercosmetics = new CosmeticDatabase.PlayerCosmetics(
                  resultset.getString("tag_id"), resultset.getString("tag_display"), resultset.getString("name_color"), resultset.getString("chat_color")
               );
            }
         }

         return cosmeticdatabase$playercosmetics;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[cosmetics] Read failed: " + sqlexception.getMessage());
         return CosmeticDatabase.PlayerCosmetics.empty();
      }
   }

   public synchronized void save(UUID uuid, CosmeticDatabase.PlayerCosmetics data) {
      this.cache.put(uuid, data);

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO player_cosmetics (uuid, tag_id, tag_display, name_color, chat_color)\nVALUES (?, ?, ?, ?, ?)\nON CONFLICT(uuid) DO UPDATE SET\n    tag_id = excluded.tag_id,\n    tag_display = excluded.tag_display,\n    name_color = excluded.name_color,\n    chat_color = excluded.chat_color\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, data.tagId());
         preparedstatement.setString(3, data.tagDisplay());
         preparedstatement.setString(4, data.nameColor());
         preparedstatement.setString(5, data.chatColor());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[cosmetics] Save failed: " + sqlexception.getMessage());
      }
   }

   public synchronized void close() {
      this.cache.clear();

      try {
         if (this.connection != null && !this.connection.isClosed()) {
            this.connection.close();
         }
      } catch (SQLException sqlexception) {
      }

      this.connection = null;
   }

   public static record PlayerCosmetics(String tagId, String tagDisplay, String nameColor, String chatColor) {
      public static CosmeticDatabase.PlayerCosmetics empty() {
         return new CosmeticDatabase.PlayerCosmetics(null, null, null, null);
      }

      public CosmeticDatabase.PlayerCosmetics withTag(String id, String display) {
         return new CosmeticDatabase.PlayerCosmetics(id, display, this.nameColor, this.chatColor);
      }

      public CosmeticDatabase.PlayerCosmetics withoutTag() {
         return new CosmeticDatabase.PlayerCosmetics(null, null, this.nameColor, this.chatColor);
      }

      public CosmeticDatabase.PlayerCosmetics withNameColor(String color) {
         return new CosmeticDatabase.PlayerCosmetics(this.tagId, this.tagDisplay, color, this.chatColor);
      }

      public CosmeticDatabase.PlayerCosmetics withoutNameColor() {
         return new CosmeticDatabase.PlayerCosmetics(this.tagId, this.tagDisplay, null, this.chatColor);
      }

      public CosmeticDatabase.PlayerCosmetics withChatColor(String color) {
         return new CosmeticDatabase.PlayerCosmetics(this.tagId, this.tagDisplay, this.nameColor, color);
      }

      public CosmeticDatabase.PlayerCosmetics withoutChatColor() {
         return new CosmeticDatabase.PlayerCosmetics(this.tagId, this.tagDisplay, this.nameColor, null);
      }
   }
}

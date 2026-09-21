package com.sharded.core.modules.wardrobe;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class WardrobeDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public WardrobeDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file1 = new File(folder, "wardrobe.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

      try (Statement statement = this.connection.createStatement()) {
         statement.execute(
            "CREATE TABLE IF NOT EXISTS wardrobe_owned (\n    uuid TEXT NOT NULL,\n    hat_id TEXT NOT NULL,\n    PRIMARY KEY (uuid, hat_id)\n)\n"
         );
         statement.execute("CREATE TABLE IF NOT EXISTS wardrobe_equipped (\n    uuid TEXT PRIMARY KEY,\n    hat_id TEXT NOT NULL\n)\n");
         statement.execute("CREATE TABLE IF NOT EXISTS wardrobe_helmet (\n    uuid TEXT PRIMARY KEY,\n    helmet BLOB\n)\n");
         statement.execute(
            "CREATE TABLE IF NOT EXISTS wardrobe_favourite (\n    uuid TEXT NOT NULL,\n    hat_id TEXT NOT NULL,\n    PRIMARY KEY (uuid, hat_id)\n)\n"
         );
      }
   }

   public synchronized boolean isFavourite(UUID uuid, String hatId) {
      if (uuid == null || hatId == null) {
         return false;
      }
      String id = hatId.toLowerCase();
      try (PreparedStatement preparedstatement = this.connection.prepareStatement(
         "SELECT 1 FROM wardrobe_favourite WHERE uuid = ? AND lower(hat_id) = ?"
      )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, id);
         try (ResultSet resultset = preparedstatement.executeQuery()) {
            return resultset.next();
         }
      } catch (SQLException sqlexception) {
         return false;
      }
   }

   public synchronized boolean toggleFavourite(UUID uuid, String hatId) {
      if (this.isFavourite(uuid, hatId)) {
         this.clearFavourite(uuid, hatId);
         return false;
      }
      try (PreparedStatement preparedstatement = this.connection.prepareStatement(
         "INSERT OR IGNORE INTO wardrobe_favourite (uuid, hat_id) VALUES (?, ?)"
      )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, hatId.toLowerCase());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception1) {
         this.plugin.getLogger().warning("Failed to favourite hat: " + sqlexception1.getMessage());
      }
      return true;
   }

   public synchronized void clearFavourite(UUID uuid, String hatId) {
      try (PreparedStatement preparedstatement1 = this.connection.prepareStatement(
         "DELETE FROM wardrobe_favourite WHERE uuid = ? AND lower(hat_id) = ?"
      )) {
         preparedstatement1.setString(1, uuid.toString());
         preparedstatement1.setString(2, hatId.toLowerCase());
         preparedstatement1.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to unfavourite hat: " + sqlexception.getMessage());
      }
   }

   public synchronized void unlock(UUID uuid, String hatId) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("INSERT OR IGNORE INTO wardrobe_owned (uuid, hat_id) VALUES (?, ?)")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, hatId);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to unlock hat: " + sqlexception.getMessage());
      }
   }

   public synchronized boolean isUnlocked(UUID uuid, String hatId) {
      try {
         boolean flag;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT 1 FROM wardrobe_owned WHERE uuid = ? AND hat_id = ?")) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setString(2, hatId);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               flag = resultset.next();
            }
         }

         return flag;
      } catch (SQLException sqlexception) {
         return false;
      }
   }

   public synchronized void setEquipped(UUID uuid, String hatId) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("INSERT INTO wardrobe_equipped (uuid, hat_id) VALUES (?, ?)\nON CONFLICT(uuid) DO UPDATE SET hat_id = excluded.hat_id\n")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, hatId == null ? "" : hatId);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to save equipped hat: " + sqlexception.getMessage());
      }
   }

   public synchronized String getEquipped(UUID uuid) {
      try {
         String s;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT hat_id FROM wardrobe_equipped WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               s = resultset.next() ? resultset.getString("hat_id") : null;
            }
         }

         return s;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized void saveHelmet(UUID uuid, ItemStack helmet) {
      if (uuid != null) {
         if (helmet != null && !helmet.getType().isAir()) {
            try (PreparedStatement preparedstatement = this.connection
                  .prepareStatement("INSERT INTO wardrobe_helmet (uuid, helmet) VALUES (?, ?)\nON CONFLICT(uuid) DO UPDATE SET helmet = excluded.helmet\n")) {
               preparedstatement.setString(1, uuid.toString());
               preparedstatement.setBytes(2, helmet.serializeAsBytes());
               preparedstatement.executeUpdate();
            } catch (SQLException sqlexception) {
               this.plugin.getLogger().warning("Failed to save replaced helmet: " + sqlexception.getMessage());
            }
         } else {
            this.clearHelmet(uuid);
         }
      }
   }

   public synchronized ItemStack loadHelmet(UUID uuid) {
      if (uuid == null) {
         return null;
      } else {
         try {
            Object object;
            try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT helmet FROM wardrobe_helmet WHERE uuid = ?")) {
               preparedstatement.setString(1, uuid.toString());

               try (ResultSet resultset = preparedstatement.executeQuery()) {
                  if (!resultset.next()) {
                     return null;
                  }

                  byte[] abyte = resultset.getBytes("helmet");
                  if (abyte != null && abyte.length != 0) {
                     return ItemStack.deserializeBytes(abyte);
                  }

                  object = null;
               }
            }

            return (ItemStack)object;
         } catch (Exception exception) {
            this.plugin.getLogger().warning("Failed to load replaced helmet: " + exception.getMessage());
            return null;
         }
      }
   }

   public synchronized void clearHelmet(UUID uuid) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM wardrobe_helmet WHERE uuid = ?")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to clear replaced helmet: " + sqlexception.getMessage());
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
}

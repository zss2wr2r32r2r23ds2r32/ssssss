package com.sharded.core.modules.namecolor;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class NameColorDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public NameColorDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file1 = new File(folder, "namecolor.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

      try (Statement statement = this.connection.createStatement()) {
         statement.execute("CREATE TABLE IF NOT EXISTS player_namecolor (\n    uuid TEXT PRIMARY KEY,\n    last_gradient TEXT\n)\n");
      }
   }

   public synchronized String getLastGradient(UUID uuid) {
      try {
         String s;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT last_gradient FROM player_namecolor WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               s = resultset.getString("last_gradient");
            }
         }

         return s;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to read name gradient: " + sqlexception.getMessage());
         return null;
      }
   }

   public synchronized void saveLastGradient(UUID uuid, String gradient) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO player_namecolor (uuid, last_gradient) VALUES (?, ?)\nON CONFLICT(uuid) DO UPDATE SET last_gradient = excluded.last_gradient\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, gradient);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to save name gradient: " + sqlexception.getMessage());
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

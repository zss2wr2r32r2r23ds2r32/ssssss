package com.sharded.core.modules.backpack;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ItemSerializer;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class BackpackDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public BackpackDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file1 = new File(folder, "backpacks.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

      try (Statement statement = this.connection.createStatement()) {
         statement.execute("CREATE TABLE IF NOT EXISTS backpacks (\n    uuid TEXT PRIMARY KEY,\n    data TEXT NOT NULL,\n    updated_at INTEGER NOT NULL\n)\n");
      }
   }

   public synchronized ItemStack[] load(UUID uuid) {
      try {
         ItemStack[] aitemstack;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT data FROM backpacks WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return new ItemStack[0];
               }

               aitemstack = ItemSerializer.fromBase64(resultset.getString("data"));
            }
         }

         return aitemstack;
      } catch (Exception exception) {
         this.plugin.getLogger().severe("Failed to load backpack for " + uuid + ": " + exception.getMessage());
         return new ItemStack[0];
      }
   }

   public synchronized void save(UUID uuid, ItemStack[] items) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO backpacks (uuid, data, updated_at) VALUES (?, ?, ?)\nON CONFLICT(uuid) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, ItemSerializer.toBase64(items));
         preparedstatement.setLong(3, System.currentTimeMillis());
         preparedstatement.executeUpdate();
      } catch (Exception exception) {
         this.plugin.getLogger().severe("Failed to save backpack for " + uuid + ": " + exception.getMessage());
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

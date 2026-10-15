package com.sharded.core.modules.pets;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class PetDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public PetDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file1 = new File(folder, "pets.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

      try (Statement statement = this.connection.createStatement()) {
         statement.execute(
            "CREATE TABLE IF NOT EXISTS player_pets (\n    uuid TEXT PRIMARY KEY,\n    pet_type TEXT NOT NULL,\n    pet_name TEXT,\n    pet_variant TEXT\n)\n"
         );

         try {
            statement.execute("ALTER TABLE player_pets ADD COLUMN pet_variant TEXT");
         } catch (SQLException sqlexception) {
         }
      }
   }

   public synchronized PetDatabase.PetRecord get(UUID uuid) {
      try {
         PetDatabase.PetRecord petdatabase$petrecord;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT pet_type, pet_name, pet_variant FROM player_pets WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               petdatabase$petrecord = new PetDatabase.PetRecord(
                  PetType.fromId(resultset.getString("pet_type")), resultset.getString("pet_name"), resultset.getString("pet_variant")
               );
            }
         }

         return petdatabase$petrecord;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to read pet: " + sqlexception.getMessage());
         return null;
      }
   }

   public synchronized void save(UUID uuid, PetType type, String name, String variant) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO player_pets (uuid, pet_type, pet_name, pet_variant) VALUES (?, ?, ?, ?)\nON CONFLICT(uuid) DO UPDATE SET\n    pet_type = excluded.pet_type,\n    pet_name = excluded.pet_name,\n    pet_variant = excluded.pet_variant\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, type.id());
         preparedstatement.setString(3, name);
         preparedstatement.setString(4, variant);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to save pet: " + sqlexception.getMessage());
      }
   }

   public synchronized void clear(UUID uuid) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM player_pets WHERE uuid = ?")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to clear pet: " + sqlexception.getMessage());
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

   public static record PetRecord(PetType type, String name, String variant) {
   }
}

package com.sharded.core.modules.pets;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public final class PetDatabase {

    private final ShardedCore plugin;
    private Connection connection;

    public PetDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "pets.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_pets (
                        uuid TEXT PRIMARY KEY,
                        pet_type TEXT NOT NULL,
                        pet_name TEXT
                    )
                    """);
        }
    }

    public synchronized PetRecord get(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pet_type, pet_name FROM player_pets WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PetRecord(
                        PetType.fromId(rs.getString("pet_type")),
                        rs.getString("pet_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read pet: " + e.getMessage());
            return null;
        }
    }

    public synchronized void save(UUID uuid, PetType type, String name) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_pets (uuid, pet_type, pet_name) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET pet_type = excluded.pet_type, pet_name = excluded.pet_name
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.id());
            ps.setString(3, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save pet: " + e.getMessage());
        }
    }

    public synchronized void clear(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_pets WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to clear pet: " + e.getMessage());
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }

    public record PetRecord(PetType type, String name) {
    }
}

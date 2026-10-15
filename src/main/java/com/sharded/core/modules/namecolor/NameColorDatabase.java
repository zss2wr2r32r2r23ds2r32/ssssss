package com.sharded.core.modules.namecolor;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public final class NameColorDatabase {

    private final ShardedCore plugin;
    private Connection connection;

    public NameColorDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "namecolor.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_namecolor (
                        uuid TEXT PRIMARY KEY,
                        last_gradient TEXT
                    )
                    """);
        }
    }

    public synchronized String getLastGradient(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_gradient FROM player_namecolor WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("last_gradient");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read name gradient: " + e.getMessage());
            return null;
        }
    }

    public synchronized void saveLastGradient(UUID uuid, String gradient) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_namecolor (uuid, last_gradient) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET last_gradient = excluded.last_gradient
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, gradient);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save name gradient: " + e.getMessage());
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}

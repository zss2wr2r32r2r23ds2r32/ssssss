package com.sharded.core.modules.tags;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public final class TagDatabase {

    private final ShardedCore plugin;
    private Connection connection;

    public TagDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "tags.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_tags (
                        uuid TEXT PRIMARY KEY,
                        last_custom_tag TEXT NOT NULL
                    )
                    """);
        }
    }

    public synchronized String getLastCustomTag(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_custom_tag FROM player_tags WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("last_custom_tag");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read custom tag: " + e.getMessage());
            return null;
        }
    }

    public synchronized void saveLastCustomTag(UUID uuid, String tag) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_tags (uuid, last_custom_tag) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET last_custom_tag = excluded.last_custom_tag
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, tag);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save custom tag: " + e.getMessage());
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

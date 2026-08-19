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
                        last_custom_tag TEXT,
                        last_custom_created_at INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            try {
                statement.execute("ALTER TABLE player_tags ADD COLUMN last_custom_created_at INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
            }
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

    public synchronized long getLastCustomCreatedAt(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_custom_created_at FROM player_tags WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return 0L;
                return rs.getLong("last_custom_created_at");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read custom tag cooldown: " + e.getMessage());
            return 0L;
        }
    }

    public synchronized void saveLastCustomTag(UUID uuid, String tag, boolean updateCreatedAt) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_tags (uuid, last_custom_tag, last_custom_created_at) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_custom_tag = excluded.last_custom_tag,
                    last_custom_created_at = CASE
                        WHEN excluded.last_custom_created_at > 0 THEN excluded.last_custom_created_at
                        ELSE player_tags.last_custom_created_at
                    END
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, tag);
            ps.setLong(3, updateCreatedAt ? System.currentTimeMillis() : 0L);
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

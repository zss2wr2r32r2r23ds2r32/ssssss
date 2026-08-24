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

public final class CosmeticDatabase {

    private final ShardedCore plugin;
    private Connection connection;

    public CosmeticDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "cosmetics.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_cosmetics (
                        uuid TEXT PRIMARY KEY,
                        tag_id TEXT,
                        tag_display TEXT,
                        name_color TEXT,
                        chat_color TEXT
                    )
                    """);
        }
    }

    public synchronized PlayerCosmetics get(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT tag_id, tag_display, name_color, chat_color FROM player_cosmetics WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return PlayerCosmetics.empty();
                return new PlayerCosmetics(
                        rs.getString("tag_id"),
                        rs.getString("tag_display"),
                        rs.getString("name_color"),
                        rs.getString("chat_color"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[cosmetics] Read failed: " + e.getMessage());
            return PlayerCosmetics.empty();
        }
    }

    public synchronized void save(UUID uuid, PlayerCosmetics data) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_cosmetics (uuid, tag_id, tag_display, name_color, chat_color)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    tag_id = excluded.tag_id,
                    tag_display = excluded.tag_display,
                    name_color = excluded.name_color,
                    chat_color = excluded.chat_color
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, data.tagId());
            ps.setString(3, data.tagDisplay());
            ps.setString(4, data.nameColor());
            ps.setString(5, data.chatColor());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[cosmetics] Save failed: " + e.getMessage());
        }
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }

    public record PlayerCosmetics(String tagId, String tagDisplay, String nameColor, String chatColor) {
        public static PlayerCosmetics empty() {
            return new PlayerCosmetics(null, null, null, null);
        }

        public PlayerCosmetics withTag(String id, String display) {
            return new PlayerCosmetics(id, display, nameColor, chatColor);
        }

        public PlayerCosmetics withoutTag() {
            return new PlayerCosmetics(null, null, nameColor, chatColor);
        }

        public PlayerCosmetics withNameColor(String color) {
            return new PlayerCosmetics(tagId, tagDisplay, color, chatColor);
        }

        public PlayerCosmetics withoutNameColor() {
            return new PlayerCosmetics(tagId, tagDisplay, null, chatColor);
        }

        public PlayerCosmetics withChatColor(String color) {
            return new PlayerCosmetics(tagId, tagDisplay, nameColor, color);
        }

        public PlayerCosmetics withoutChatColor() {
            return new PlayerCosmetics(tagId, tagDisplay, nameColor, null);
        }
    }
}

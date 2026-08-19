package com.sharded.core.modules.wardrobe;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.UUID;

public final class WardrobeDatabase {

    private final ShardedCore plugin;
    private Connection connection;

    public WardrobeDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "wardrobe.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wardrobe_owned (
                        uuid TEXT NOT NULL,
                        hat_id TEXT NOT NULL,
                        PRIMARY KEY (uuid, hat_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS wardrobe_equipped (
                        uuid TEXT PRIMARY KEY,
                        hat_id TEXT NOT NULL
                    )
                    """);
        }
    }

    public synchronized void unlock(UUID uuid, String hatId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO wardrobe_owned (uuid, hat_id) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, hatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to unlock hat: " + e.getMessage());
        }
    }

    public synchronized boolean isUnlocked(UUID uuid, String hatId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM wardrobe_owned WHERE uuid = ? AND hat_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, hatId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void setEquipped(UUID uuid, String hatId) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO wardrobe_equipped (uuid, hat_id) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET hat_id = excluded.hat_id
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, hatId == null ? "" : hatId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save equipped hat: " + e.getMessage());
        }
    }

    public synchronized String getEquipped(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT hat_id FROM wardrobe_equipped WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString("hat_id");
            }
        } catch (SQLException e) {
            return null;
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

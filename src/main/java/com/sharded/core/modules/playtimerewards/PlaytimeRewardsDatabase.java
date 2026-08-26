package com.sharded.core.modules.playtimerewards;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class PlaytimeRewardsDatabase {

    private final ShardedCore plugin;
    private final Connection connection;
    private final Set<String> loaded = new HashSet<>();

    PlaytimeRewardsDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "playtimerewards.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS claimed (
                        uuid TEXT NOT NULL,
                        reward_id TEXT NOT NULL,
                        claimed_at INTEGER NOT NULL,
                        PRIMARY KEY (uuid, reward_id)
                    )
                    """);
        }
    }

    synchronized boolean isClaimed(UUID uuid, String rewardId) {
        String key = uuid + "|" + rewardId;
        if (loaded.contains(key)) return true;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM claimed WHERE uuid = ? AND reward_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rewardId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    loaded.add(key);
                    return true;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to read playtime reward claim: " + e.getMessage());
        }
        return false;
    }

    synchronized void markClaimed(UUID uuid, String rewardId) {
        loaded.add(uuid + "|" + rewardId);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO claimed (uuid, reward_id, claimed_at) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, rewardId);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save playtime reward claim: " + e.getMessage());
        }
    }

    synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        loaded.clear();
    }
}

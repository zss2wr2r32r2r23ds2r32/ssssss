package com.sharded.core.modules.killstreaks;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KillstreakDatabase {

    public record LeaderEntry(UUID uuid, long value) {
    }

    private final ShardedCore plugin;
    private Connection connection;

    public KillstreakDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "killstreaks.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS killstreaks (
                        uuid TEXT PRIMARY KEY,
                        current_streak INTEGER NOT NULL DEFAULT 0,
                        best_streak INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    public synchronized int getCurrent(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT current_streak FROM killstreaks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("current_streak") : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized int getBest(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT best_streak FROM killstreaks WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("best_streak") : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized void setStreak(UUID uuid, int current) {
        int best = Math.max(getBest(uuid), current);
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO killstreaks (uuid, current_streak, best_streak) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET current_streak = excluded.current_streak, best_streak = excluded.best_streak
                """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, current);
            ps.setInt(3, best);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save killstreak: " + e.getMessage());
        }
    }

    public synchronized List<LeaderEntry> topBest(int limit) {
        List<LeaderEntry> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, best_streak FROM killstreaks ORDER BY best_streak DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LeaderEntry(UUID.fromString(rs.getString("uuid")), rs.getInt("best_streak")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to read killstreak leaderboard: " + e.getMessage());
        }
        return list;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}

package com.sharded.core.modules.tokens;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TokenDatabase {

    public record LeaderEntry(UUID uuid, long value) {
    }

    private final ShardedCore plugin;
    private Connection connection;

    public TokenDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "tokens.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS tokens (
                        uuid TEXT PRIMARY KEY,
                        balance INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public synchronized long getBalance(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT balance FROM tokens WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("balance") : 0L;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to read tokens: " + e.getMessage());
            return 0L;
        }
    }

    public synchronized void setBalance(UUID uuid, long balance) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO tokens (uuid, balance, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance, updated_at = excluded.updated_at
                """)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, Math.max(0, balance));
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save tokens: " + e.getMessage());
        }
    }

    public synchronized List<LeaderEntry> top(int limit) {
        List<LeaderEntry> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, balance FROM tokens ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LeaderEntry(UUID.fromString(rs.getString("uuid")), rs.getLong("balance")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to read token leaderboard: " + e.getMessage());
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

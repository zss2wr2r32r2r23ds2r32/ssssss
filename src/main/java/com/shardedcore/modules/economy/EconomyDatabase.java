package com.shardedcore.modules.economy;

import com.shardedcore.ShardedCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class EconomyDatabase {

    public record LeaderEntry(UUID uuid, long balance) {
    }

    public record AccountRow(long balance, boolean frozen, boolean exists) {
    }

    private final ShardedCore plugin;
    private Connection connection;

    public EconomyDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "economy.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS balance (
                        uuid TEXT PRIMARY KEY,
                        balance INTEGER NOT NULL DEFAULT 0,
                        frozen INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public synchronized AccountRow getAccount(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT balance, frozen FROM balance WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new AccountRow(0L, false, false);
                }
                return new AccountRow(rs.getLong("balance"), rs.getInt("frozen") == 1, true);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to read economy balance: " + e.getMessage());
            return new AccountRow(0L, false, false);
        }
    }

    public synchronized void upsert(UUID uuid, Long balance, Boolean frozen) {
        AccountRow current = getAccount(uuid);
        long newBalance = balance != null ? Math.max(0L, balance) : current.balance();
        boolean newFrozen = frozen != null ? frozen : current.frozen();
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO balance (uuid, balance, frozen, updated_at) VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    balance = excluded.balance,
                    frozen = excluded.frozen,
                    updated_at = excluded.updated_at
                """)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, newBalance);
            ps.setInt(3, newFrozen ? 1 : 0);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save economy balance: " + e.getMessage());
        }
    }

    public synchronized List<LeaderEntry> top(int limit) {
        List<LeaderEntry> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, balance FROM balance ORDER BY balance DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LeaderEntry(UUID.fromString(rs.getString("uuid")), rs.getLong("balance")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to read economy leaderboard: " + e.getMessage());
        }
        return list;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}

package com.sharded.core.modules.tokens;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class TokenDatabase {

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
            plugin.getLogger().severe("Failed to read tokens for " + uuid + ": " + e.getMessage());
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
            plugin.getLogger().severe("Failed to save tokens for " + uuid + ": " + e.getMessage());
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

package com.sharded.core.modules.joincounter;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class JoinCounterDatabase {

    private final ShardedCore plugin;
    private final Connection connection;
    private final Set<UUID> joined = new HashSet<>();
    private long counter;

    JoinCounterDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "joincounter.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS meta (
                        meta_key TEXT PRIMARY KEY,
                        meta_value TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS joined_players (
                        uuid TEXT PRIMARY KEY
                    )
                    """);
        }
        load();
    }

    private synchronized void load() {
        counter = 0L;
        joined.clear();
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery("SELECT meta_value FROM meta WHERE meta_key = 'counter'")) {
                if (rs.next()) counter = Long.parseLong(rs.getString("meta_value"));
            }
            try (ResultSet rs = statement.executeQuery("SELECT uuid FROM joined_players")) {
                while (rs.next()) joined.add(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load join counter database: " + e.getMessage());
        }
    }

    synchronized long counter() {
        return counter;
    }

    synchronized boolean markJoined(UUID uuid) {
        if (joined.contains(uuid)) return false;
        joined.add(uuid);
        counter++;
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT OR IGNORE INTO joined_players (uuid) VALUES (?)")) {
            insert.setString(1, uuid.toString());
            insert.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to record join for " + uuid + ": " + e.getMessage());
        }
        saveCounter();
        return true;
    }

    synchronized void resetCounter() {
        counter = joined.size();
        saveCounter();
    }

    synchronized void setCounter(long value) {
        counter = value;
        saveCounter();
    }

    private void saveCounter() {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO meta (meta_key, meta_value) VALUES ('counter', ?)
                ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value
                """)) {
            ps.setString(1, String.valueOf(counter));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save join counter: " + e.getMessage());
        }
    }

    synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
    }
}

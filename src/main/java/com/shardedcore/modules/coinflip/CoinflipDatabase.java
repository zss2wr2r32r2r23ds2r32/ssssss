package com.shardedcore.modules.coinflip;

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

public final class CoinflipDatabase {

    public record OpenGame(long id, UUID creator, long amount, long createdAt) {
    }

    public record HistoryEntry(long id, UUID winner, UUID loser, long amount, boolean creatorWon, long finishedAt) {
    }

    public record Stats(int wins, int losses, long won, long lost) {
        public static Stats empty() {
            return new Stats(0, 0, 0L, 0L);
        }
    }

    private final ShardedCore plugin;
    private Connection connection;

    public CoinflipDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "coinflip.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS open_games (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        creator_uuid TEXT NOT NULL UNIQUE,
                        amount INTEGER NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        winner_uuid TEXT NOT NULL,
                        loser_uuid TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        creator_won INTEGER NOT NULL,
                        finished_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public synchronized OpenGame createGame(UUID creator, long amount) {
        deleteGame(creator);
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO open_games (creator_uuid, amount, created_at) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, creator.toString());
            ps.setLong(2, amount);
            ps.setLong(3, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return null;
                return new OpenGame(keys.getLong(1), creator, amount, now);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[coinflip] Failed to create game: " + e.getMessage());
            return null;
        }
    }

    public synchronized void deleteGame(UUID creator) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM open_games WHERE creator_uuid = ?")) {
            ps.setString(1, creator.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[coinflip] Failed to delete game: " + e.getMessage());
        }
    }

    public synchronized OpenGame getGame(UUID creator) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM open_games WHERE creator_uuid = ?")) {
            ps.setString(1, creator.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return readOpen(rs);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized List<OpenGame> listOpenGames() {
        List<OpenGame> games = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT * FROM open_games ORDER BY amount DESC")) {
            while (rs.next()) games.add(readOpen(rs));
        } catch (SQLException e) {
            plugin.getLogger().warning("[coinflip] Failed to list games: " + e.getMessage());
        }
        return games;
    }

    public synchronized void recordHistory(UUID winner, UUID loser, long amount, boolean creatorWon) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO history (winner_uuid, loser_uuid, amount, creator_won, finished_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, winner.toString());
            ps.setString(2, loser.toString());
            ps.setLong(3, amount);
            ps.setInt(4, creatorWon ? 1 : 0);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[coinflip] Failed to record history: " + e.getMessage());
        }
    }

    public synchronized List<HistoryEntry> history(UUID uuid, int limit) {
        List<HistoryEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT * FROM history
                WHERE winner_uuid = ? OR loser_uuid = ?
                ORDER BY finished_at DESC
                LIMIT ?
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) entries.add(readHistory(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[coinflip] Failed to load history: " + e.getMessage());
        }
        return entries;
    }

    public synchronized Stats stats(UUID uuid) {
        int wins = 0;
        int losses = 0;
        long won = 0L;
        long lost = 0L;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT winner_uuid, loser_uuid, amount FROM history
                WHERE winner_uuid = ? OR loser_uuid = ?
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long amount = rs.getLong("amount");
                    if (uuid.toString().equals(rs.getString("winner_uuid"))) {
                        wins++;
                        won += amount;
                    } else {
                        losses++;
                        lost += amount;
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[coinflip] Failed to load stats: " + e.getMessage());
        }
        return new Stats(wins, losses, won, lost);
    }

    private static OpenGame readOpen(ResultSet rs) throws SQLException {
        return new OpenGame(
                rs.getLong("id"),
                UUID.fromString(rs.getString("creator_uuid")),
                rs.getLong("amount"),
                rs.getLong("created_at")
        );
    }

    private static HistoryEntry readHistory(ResultSet rs) throws SQLException {
        return new HistoryEntry(
                rs.getLong("id"),
                UUID.fromString(rs.getString("winner_uuid")),
                UUID.fromString(rs.getString("loser_uuid")),
                rs.getLong("amount"),
                rs.getInt("creator_won") == 1,
                rs.getLong("finished_at")
        );
    }

    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}

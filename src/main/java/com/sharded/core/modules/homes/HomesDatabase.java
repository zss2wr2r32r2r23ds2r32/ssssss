package com.sharded.core.modules.homes;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.*;

public final class HomesDatabase {

    public record Home(UUID uuid, int slot, String world, double x, double y, double z, float yaw, float pitch) {
    }

    private final ShardedCore plugin;
    private Connection connection;

    public HomesDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "homes.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS homes (
                        uuid TEXT NOT NULL,
                        slot INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL DEFAULT 0,
                        pitch REAL NOT NULL DEFAULT 0,
                        PRIMARY KEY (uuid, slot)
                    )
                    """);
        }
    }

    public synchronized void setHome(UUID uuid, int slot, String world, double x, double y, double z, float yaw, float pitch) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO homes (uuid, slot, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid, slot) DO UPDATE SET
                    world = excluded.world,
                    x = excluded.x,
                    y = excluded.y,
                    z = excluded.z,
                    yaw = excluded.yaw,
                    pitch = excluded.pitch
                """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.setString(3, world);
            ps.setDouble(4, x);
            ps.setDouble(5, y);
            ps.setDouble(6, z);
            ps.setFloat(7, yaw);
            ps.setFloat(8, pitch);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[homes] Failed to set home: " + e.getMessage());
        }
    }

    public synchronized void deleteHome(UUID uuid, int slot) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM homes WHERE uuid = ? AND slot = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[homes] Failed to delete home: " + e.getMessage());
        }
    }

    public synchronized Home getHome(UUID uuid, int slot) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM homes WHERE uuid = ? AND slot = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return readRow(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[homes] Failed to get home: " + e.getMessage());
            return null;
        }
    }

    public synchronized List<Home> listHomes(UUID uuid) {
        List<Home> homes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM homes WHERE uuid = ? ORDER BY slot ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) homes.add(readRow(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[homes] Failed to list homes: " + e.getMessage());
        }
        return homes;
    }

    public synchronized int countHomes(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM homes WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized boolean hasHome(UUID uuid, int slot) {
        return getHome(uuid, slot) != null;
    }

    private static Home readRow(ResultSet rs) throws SQLException {
        return new Home(
                UUID.fromString(rs.getString("uuid")),
                rs.getInt("slot"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch")
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

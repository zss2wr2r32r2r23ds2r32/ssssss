package com.shardedcore.modules.commands.homes;

import com.shardedcore.ShardedCore;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

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
                    world = excluded.world, x = excluded.x, y = excluded.y, z = excluded.z,
                    yaw = excluded.yaw, pitch = excluded.pitch
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
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM homes WHERE uuid = ? AND slot = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[homes] Failed to delete home: " + e.getMessage());
        }
    }

    public synchronized Home getHome(UUID uuid, int slot) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM homes WHERE uuid = ? AND slot = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, slot);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Home(UUID.fromString(rs.getString("uuid")), rs.getInt("slot"), rs.getString("world"),
                        rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[homes] Failed to get home: " + e.getMessage());
            return null;
        }
    }

    public synchronized boolean hasHome(UUID uuid, int slot) {
        return getHome(uuid, slot) != null;
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

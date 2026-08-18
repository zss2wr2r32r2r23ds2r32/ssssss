package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.UUID;

/**
 * SQLite-backed per-player key/value store for toggles and module state.
 * Migrates existing players.yml on first boot.
 */
public final class PlayerStateStore {

    private final ShardedCore plugin;
    private Connection connection;

    public PlayerStateStore(ShardedCore plugin) {
        this.plugin = plugin;
        try {
            File dbFile = new File(plugin.getDataFolder(), "player-state.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS player_state (
                            uuid TEXT NOT NULL,
                            state_key TEXT NOT NULL,
                            bool_value INTEGER,
                            long_value INTEGER,
                            string_value TEXT,
                            PRIMARY KEY (uuid, state_key)
                        )
                        """);
            }
            migrateFromYaml();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open player-state database", e);
        }
    }

    private void migrateFromYaml() {
        File legacy = new File(plugin.getDataFolder(), "players.yml");
        if (!legacy.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy);
        if (!yaml.getKeys(false).iterator().hasNext()) return;
        int migrated = 0;
        for (String uuidKey : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                var section = yaml.getConfigurationSection(uuidKey);
                if (section == null) continue;
                for (String key : section.getKeys(false)) {
                    Object value = section.get(key);
                    if (value instanceof Boolean b) {
                        setBool(uuid, key, b);
                        migrated++;
                    } else if (value instanceof Number n) {
                        setLong(uuid, key, n.longValue());
                        migrated++;
                    } else if (value instanceof String s) {
                        setString(uuid, key, s);
                        migrated++;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (migrated > 0) {
            plugin.getLogger().info("Migrated " + migrated + " player state entries from players.yml to SQLite.");
            File backup = new File(plugin.getDataFolder(), "players.yml.bak");
            if (!legacy.renameTo(backup)) {
                plugin.getLogger().warning("Could not rename players.yml after migration.");
            }
        }
    }

    public synchronized boolean getBool(UUID uuid, String key, boolean def) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT bool_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject("bool_value") == null) return def;
                return rs.getInt("bool_value") == 1;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read bool state " + key + ": " + e.getMessage());
            return def;
        }
    }

    public synchronized void setBool(UUID uuid, String key, boolean value) {
        upsert(uuid, key, value ? 1 : 0, null, null);
    }

    public synchronized long getLong(UUID uuid, String key, long def) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT long_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getObject("long_value") == null) return def;
                return rs.getLong("long_value");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read long state " + key + ": " + e.getMessage());
            return def;
        }
    }

    public synchronized void setLong(UUID uuid, String key, long value) {
        upsert(uuid, key, null, value, null);
    }

    public synchronized String getString(UUID uuid, String key, String def) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT string_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getString("string_value") == null) return def;
                return rs.getString("string_value");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read string state " + key + ": " + e.getMessage());
            return def;
        }
    }

    public synchronized void setString(UUID uuid, String key, String value) {
        upsert(uuid, key, null, null, value);
    }

    private void upsert(UUID uuid, String key, Integer boolVal, Long longVal, String stringVal) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_state (uuid, state_key, bool_value, long_value, string_value)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(uuid, state_key) DO UPDATE SET
                    bool_value = COALESCE(excluded.bool_value, player_state.bool_value),
                    long_value = COALESCE(excluded.long_value, player_state.long_value),
                    string_value = COALESCE(excluded.string_value, player_state.string_value)
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, key);
            if (boolVal != null) ps.setInt(3, boolVal);
            else ps.setNull(3, Types.INTEGER);
            if (longVal != null) ps.setLong(4, longVal);
            else ps.setNull(4, Types.INTEGER);
            if (stringVal != null) ps.setString(5, stringVal);
            else ps.setNull(5, Types.VARCHAR);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save state " + key + ": " + e.getMessage());
        }
    }

    public synchronized void saveNow() {
        // SQLite commits immediately; kept for API compatibility.
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}

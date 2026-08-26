package com.shardedcore.util;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.SqliteDatabase;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class PlayerStateStore implements AutoCloseable {

    private final ShardedCore plugin;
    private final SqliteDatabase database;
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public PlayerStateStore(ShardedCore plugin) {
        this.plugin = plugin;
        this.database = new SqliteDatabase(plugin);
    }

    public void init() {
        try {
            database.open();
            database.runSchema("""
                    CREATE TABLE IF NOT EXISTS player_toggles (
                        uuid TEXT NOT NULL,
                        toggle_key TEXT NOT NULL,
                        enabled INTEGER NOT NULL DEFAULT 1,
                        PRIMARY KEY (uuid, toggle_key)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize player state store", ex);
        }
    }

    public void reload() {
        cache.clear();
    }

    public boolean getToggle(UUID uuid, String key, boolean defaultValue) {
        Map<String, Boolean> playerState = cache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        return playerState.computeIfAbsent(key, ignored -> loadToggle(uuid, key, defaultValue));
    }

    public void setToggle(UUID uuid, String key, boolean enabled) {
        cache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(key, enabled);
        try {
            database.execute("""
                    INSERT INTO player_toggles (uuid, toggle_key, enabled)
                    VALUES (?, ?, ?)
                    ON CONFLICT(uuid, toggle_key) DO UPDATE SET enabled = excluded.enabled
                    """, uuid.toString(), key, enabled ? 1 : 0);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save toggle " + key + " for " + uuid, ex);
        }
    }

    public boolean toggle(UUID uuid, String key, boolean defaultValue) {
        boolean next = !getToggle(uuid, key, defaultValue);
        setToggle(uuid, key, next);
        return next;
    }

    private boolean loadToggle(UUID uuid, String key, boolean defaultValue) {
        try {
            Boolean value = database.query(
                    "SELECT enabled FROM player_toggles WHERE uuid = ? AND toggle_key = ?",
                    resultSet -> {
                        try {
                            return resultSet.next() ? resultSet.getInt("enabled") == 1 : null;
                        } catch (SQLException ex) {
                            throw new IllegalStateException(ex);
                        }
                    },
                    uuid.toString(), key
            );
            return value != null ? value : defaultValue;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load toggle " + key + " for " + uuid, ex);
            return defaultValue;
        }
    }

    @Override
    public void close() {
        cache.clear();
        database.close();
    }
}

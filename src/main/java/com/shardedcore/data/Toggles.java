package com.shardedcore.data;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Player toggles. Reads hit an in-memory cache; writes run async. */
public final class Toggles {

    private final ShardedCore plugin;
    private final Sqlite sqlite;
    private final Map<UUID, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    public Toggles(ShardedCore plugin) {
        this.plugin = plugin;
        this.sqlite = new Sqlite(plugin, new File(plugin.getDataFolder(), "data.db"));
    }

    public void init() {
        try {
            sqlite.open();
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS toggles (
                        uuid TEXT NOT NULL,
                        k TEXT NOT NULL,
                        v INTEGER NOT NULL,
                        PRIMARY KEY (uuid, k)
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to open toggle database", ex);
        }
    }

    public boolean get(UUID uuid, String key, boolean defaultValue) {
        return cache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(key, ignored -> load(uuid, key, defaultValue));
    }

    public void set(UUID uuid, String key, boolean value) {
        cache.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>()).put(key, value);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO toggles (uuid, k, v) VALUES (?, ?, ?)
                        ON CONFLICT(uuid, k) DO UPDATE SET v = excluded.v
                        """, uuid.toString(), key, value ? 1 : 0);
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save toggle " + key, ex);
            }
        });
    }

    public boolean flip(UUID uuid, String key, boolean defaultValue) {
        boolean next = !get(uuid, key, defaultValue);
        set(uuid, key, next);
        return next;
    }

    public Sqlite sqlite() {
        return sqlite;
    }

    public void close() {
        sqlite.close();
        cache.clear();
    }

    private boolean load(UUID uuid, String key, boolean defaultValue) {
        try {
            Boolean value = sqlite.query(
                    "SELECT v FROM toggles WHERE uuid = ? AND k = ?",
                    rs -> {
                        try {
                            return rs.next() ? rs.getInt("v") == 1 : null;
                        } catch (SQLException ex) {
                            throw new IllegalStateException(ex);
                        }
                    },
                    uuid.toString(), key
            );
            return value != null ? value : defaultValue;
        } catch (SQLException ex) {
            return defaultValue;
        }
    }
}

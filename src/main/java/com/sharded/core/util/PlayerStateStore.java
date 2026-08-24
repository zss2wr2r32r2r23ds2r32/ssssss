package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SQLite-backed per-player key/value store for toggles and module state.
 * Writes are debounced and flushed asynchronously; reads use an in-memory cache.
 */
public final class PlayerStateStore {

    private record CacheEntry(Boolean boolVal, Long longVal, String stringVal) {
    }

    private record PendingWrite(UUID uuid, String key, Integer boolVal, Long longVal, String stringVal) {
        String cacheKey() {
            return uuid + "|" + key;
        }
    }

    private final ShardedCore plugin;
    private Connection connection;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, PendingWrite> pending = new ConcurrentHashMap<>();
    private volatile int flushTaskId = -1;
    private static final long FLUSH_DELAY_TICKS = 40L;

    public PlayerStateStore(ShardedCore plugin) {
        this.plugin = plugin;
        try {
            File dbFile = new File(plugin.getDataFolder(), "player-state.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA synchronous=NORMAL");
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

    private static String cacheKey(UUID uuid, String key) {
        return uuid + "|" + key;
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
            flushPendingSync();
            plugin.getLogger().info("Migrated " + migrated + " player state entries from players.yml to SQLite.");
            File backup = new File(plugin.getDataFolder(), "players.yml.bak");
            if (!legacy.renameTo(backup)) {
                plugin.getLogger().warning("Could not rename players.yml after migration.");
            }
        }
    }

    public boolean getBool(UUID uuid, String key, boolean def) {
        CacheEntry cached = cache.get(cacheKey(uuid, key));
        if (cached != null && cached.boolVal != null) return cached.boolVal;
        synchronized (this) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT bool_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject("bool_value") == null) return def;
                    boolean value = rs.getInt("bool_value") == 1;
                    putCache(uuid, key, new CacheEntry(value, null, null));
                    return value;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to read bool state " + key + ": " + e.getMessage());
                return def;
            }
        }
    }

    public void setBool(UUID uuid, String key, boolean value) {
        putCache(uuid, key, new CacheEntry(value, null, null));
        queueWrite(new PendingWrite(uuid, key, value ? 1 : 0, null, null));
    }

    public long getLong(UUID uuid, String key, long def) {
        CacheEntry cached = cache.get(cacheKey(uuid, key));
        if (cached != null && cached.longVal != null) return cached.longVal;
        synchronized (this) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT long_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getObject("long_value") == null) return def;
                    long value = rs.getLong("long_value");
                    putCache(uuid, key, new CacheEntry(null, value, null));
                    return value;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to read long state " + key + ": " + e.getMessage());
                return def;
            }
        }
    }

    public void setLong(UUID uuid, String key, long value) {
        putCache(uuid, key, new CacheEntry(null, value, null));
        queueWrite(new PendingWrite(uuid, key, null, value, null));
    }

    public String getString(UUID uuid, String key, String def) {
        CacheEntry cached = cache.get(cacheKey(uuid, key));
        if (cached != null && cached.stringVal != null) return cached.stringVal;
        synchronized (this) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT string_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, key);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || rs.getString("string_value") == null) return def;
                    String value = rs.getString("string_value");
                    putCache(uuid, key, new CacheEntry(null, null, value));
                    return value;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to read string state " + key + ": " + e.getMessage());
                return def;
            }
        }
    }

    public void setString(UUID uuid, String key, String value) {
        putCache(uuid, key, new CacheEntry(null, null, value));
        queueWrite(new PendingWrite(uuid, key, null, null, value));
    }

    private void putCache(UUID uuid, String key, CacheEntry entry) {
        cache.put(cacheKey(uuid, key), entry);
    }

    private void queueWrite(PendingWrite write) {
        pending.put(write.cacheKey(), write);
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (flushTaskId >= 0) return;
        flushTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            flushTaskId = -1;
            flushPendingAsync();
        }, FLUSH_DELAY_TICKS).getTaskId();
    }

    private void flushPendingAsync() {
        if (pending.isEmpty()) return;
        Map<String, PendingWrite> batch = Map.copyOf(pending);
        pending.keySet().removeAll(batch.keySet());
        synchronized (this) {
            upsertBatch(batch.values());
        }
    }

    private void flushPendingSync() {
        if (pending.isEmpty()) return;
        Map<String, PendingWrite> batch = Map.copyOf(pending);
        pending.clear();
        if (flushTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        synchronized (this) {
            upsertBatch(batch.values());
        }
    }

    private void upsertBatch(Iterable<PendingWrite> writes) {
        if (connection == null) return;
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO player_state (uuid, state_key, bool_value, long_value, string_value)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(uuid, state_key) DO UPDATE SET
                        bool_value = COALESCE(excluded.bool_value, player_state.bool_value),
                        long_value = COALESCE(excluded.long_value, player_state.long_value),
                        string_value = COALESCE(excluded.string_value, player_state.string_value)
                    """)) {
                for (PendingWrite write : writes) {
                    ps.setString(1, write.uuid().toString());
                    ps.setString(2, write.key());
                    if (write.boolVal() != null) ps.setInt(3, write.boolVal());
                    else ps.setNull(3, Types.INTEGER);
                    if (write.longVal() != null) ps.setLong(4, write.longVal());
                    else ps.setNull(4, Types.INTEGER);
                    if (write.stringVal() != null) ps.setString(5, write.stringVal());
                    else ps.setNull(5, Types.VARCHAR);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            plugin.getLogger().warning("Failed to flush player state: " + e.getMessage());
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    public synchronized void clear(UUID uuid) {
        cache.keySet().removeIf(k -> k.startsWith(uuid + "|"));
        Iterator<Map.Entry<String, PendingWrite>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().uuid().equals(uuid)) it.remove();
        }
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM player_state WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to clear player state: " + e.getMessage());
        }
    }

    public void saveNow() {
        flushPendingSync();
    }

    public synchronized void close() {
        flushPendingSync();
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
        cache.clear();
    }
}

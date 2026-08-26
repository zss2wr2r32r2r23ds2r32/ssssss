package com.sharded.core.modules.tags;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TagDatabase {

    private final ShardedCore plugin;
    private Connection connection;
    private final ConcurrentHashMap<UUID, CustomTagState> cache = new ConcurrentHashMap<>();

    private record CustomTagState(String tag, long createdAt) {
    }

    public TagDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "tags.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_tags (
                        uuid TEXT PRIMARY KEY,
                        last_custom_tag TEXT,
                        last_custom_created_at INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            try {
                statement.execute("ALTER TABLE player_tags ADD COLUMN last_custom_created_at INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
            }
        }
    }

    public String getLastCustomTag(UUID uuid) {
        CustomTagState state = cache.get(uuid);
        if (state != null) return state.tag();
        state = loadState(uuid);
        if (state != null) cache.put(uuid, state);
        return state == null ? null : state.tag();
    }

    public long getLastCustomCreatedAt(UUID uuid) {
        CustomTagState state = cache.get(uuid);
        if (state != null) return state.createdAt();
        state = loadState(uuid);
        if (state != null) cache.put(uuid, state);
        return state == null ? 0L : state.createdAt();
    }

    private synchronized CustomTagState loadState(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_custom_tag, last_custom_created_at FROM player_tags WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new CustomTagState(null, 0L);
                return new CustomTagState(rs.getString("last_custom_tag"), rs.getLong("last_custom_created_at"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read custom tag: " + e.getMessage());
            return null;
        }
    }

    public void saveLastCustomTag(UUID uuid, String tag, boolean updateCreatedAt) {
        CustomTagState existing = cache.get(uuid);
        if (existing == null) {
            existing = loadState(uuid);
            if (existing == null) existing = new CustomTagState(null, 0L);
        }
        long createdAt = updateCreatedAt ? System.currentTimeMillis() : existing.createdAt();
        cache.put(uuid, new CustomTagState(tag, createdAt));
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                saveLastCustomTagSync(uuid, tag, updateCreatedAt));
    }

    private synchronized void saveLastCustomTagSync(UUID uuid, String tag, boolean updateCreatedAt) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_tags (uuid, last_custom_tag, last_custom_created_at) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_custom_tag = excluded.last_custom_tag,
                    last_custom_created_at = CASE
                        WHEN excluded.last_custom_created_at > 0 THEN excluded.last_custom_created_at
                        ELSE player_tags.last_custom_created_at
                    END
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, tag);
            ps.setLong(3, updateCreatedAt ? System.currentTimeMillis() : 0L);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save custom tag: " + e.getMessage());
        }
    }

    public synchronized void close() {
        cache.clear();
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }
}

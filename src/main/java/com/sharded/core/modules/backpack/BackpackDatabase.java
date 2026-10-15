package com.sharded.core.modules.backpack;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/** SQLite storage for backpacks (uses the SQLite driver bundled with Paper). */
public final class BackpackDatabase {

    private final ShardedCore plugin;
    private Connection connection;

    public BackpackDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "backpacks.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS backpacks (
                        uuid TEXT PRIMARY KEY,
                        data TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public synchronized ItemStack[] load(UUID uuid) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT data FROM backpacks WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return ItemSerializer.fromBase64(result.getString("data"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load backpack for " + uuid + ": " + e.getMessage());
        }
        return new ItemStack[0];
    }

    public synchronized void save(UUID uuid, ItemStack[] items) {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO backpacks (uuid, data, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, ItemSerializer.toBase64(items));
            statement.setLong(3, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save backpack for " + uuid + ": " + e.getMessage());
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

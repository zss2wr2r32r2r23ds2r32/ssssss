package com.sharded.core.modules.shop;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.*;
import java.util.*;

public final class ShopDatabase {
    public record Purchase(long id, UUID player, String section, String itemKey, int amount, long price, long time) {}
    private final ShardedCore plugin; private Connection connection;
    public ShopDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "shop-history.db").getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, section TEXT NOT NULL, item_key TEXT NOT NULL, amount INTEGER NOT NULL, price INTEGER NOT NULL, purchased_at INTEGER NOT NULL)");
        }
    }
    public synchronized void record(UUID player, String section, String itemKey, int amount, long price) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO purchases (player_uuid, section, item_key, amount, price, purchased_at) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, player.toString()); ps.setString(2, section); ps.setString(3, itemKey); ps.setInt(4, amount); ps.setLong(5, price); ps.setLong(6, System.currentTimeMillis()); ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().warning("[shop] record: "+e.getMessage()); }
    }
    public synchronized void close() { if (connection != null) { try { connection.close(); } catch (SQLException ignored) {} connection = null; } }
}

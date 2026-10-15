package com.sharded.core.modules.orders;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.*;
import java.util.*;

public final class OrdersDatabase {
    public record Order(long id, UUID owner, byte[] itemBytes, int totalAmount, int deliveredAmount,
                        int collectedAmount, long pricePerItem, long createdAt, long expiresAt) {
        boolean isComplete() { return deliveredAmount >= totalAmount; }
        int remaining() { return Math.max(0, totalAmount - deliveredAmount); }
        long totalPrice() { return pricePerItem * totalAmount; }
        long paidOut() { return pricePerItem * deliveredAmount; }
    }
    public record PlayerStats(int placed, int deliveries, long earned, long spent) {}

    private final ShardedCore plugin;
    private Connection connection;

    public OrdersDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        connection = DriverManager.getConnection("jdbc:sqlite:" + new File(folder, "orders.db").getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS orders (id INTEGER PRIMARY KEY AUTOINCREMENT, owner_uuid TEXT NOT NULL, item_bytes BLOB NOT NULL, total_amount INTEGER NOT NULL, delivered_amount INTEGER NOT NULL DEFAULT 0, collected_amount INTEGER NOT NULL DEFAULT 0, price_per_item INTEGER NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
            st.execute("CREATE TABLE IF NOT EXISTS player_stats (uuid TEXT PRIMARY KEY, placed INTEGER NOT NULL DEFAULT 0, deliveries INTEGER NOT NULL DEFAULT 0, earned INTEGER NOT NULL DEFAULT 0, spent INTEGER NOT NULL DEFAULT 0)");
        }
    }

    public synchronized long createOrder(UUID owner, byte[] itemBytes, int amount, long pricePerItem, long createdAt, long expiresAt) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO orders (owner_uuid, item_bytes, total_amount, price_per_item, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, owner.toString()); ps.setBytes(2, itemBytes); ps.setInt(3, amount); ps.setLong(4, pricePerItem); ps.setLong(5, createdAt); ps.setLong(6, expiresAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { if (keys.next()) { incrementStat(owner, "placed", 1); return keys.getLong(1); } }
        } catch (SQLException e) { plugin.getLogger().warning("[orders] create: " + e.getMessage()); }
        return -1;
    }

    public synchronized Order getOrder(long id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM orders WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return readOrder(rs); }
        } catch (SQLException ignored) {}
        return null;
    }

    public synchronized List<Order> listOpenOrders() {
        List<Order> orders = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM orders WHERE delivered_amount < total_amount AND expires_at > ? ORDER BY created_at DESC")) {
            ps.setLong(1, now);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) orders.add(readOrder(rs)); }
        } catch (SQLException e) { plugin.getLogger().warning("[orders] list: " + e.getMessage()); }
        return orders;
    }

    public synchronized List<Order> listOrdersByOwner(UUID owner) {
        List<Order> orders = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM orders WHERE owner_uuid = ? ORDER BY created_at DESC")) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) orders.add(readOrder(rs)); }
        } catch (SQLException ignored) {}
        return orders;
    }

    public synchronized int countOpenOrders(UUID owner) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM orders WHERE owner_uuid = ? AND delivered_amount < total_amount AND expires_at > ?")) {
            ps.setString(1, owner.toString()); ps.setLong(2, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (SQLException e) { return 0; }
    }

    public synchronized boolean deliver(long id, int amount) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE orders SET delivered_amount = delivered_amount + ? WHERE id = ? AND delivered_amount + ? <= total_amount")) {
            ps.setInt(1, amount); ps.setLong(2, id); ps.setInt(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public synchronized boolean collect(long id, int amount) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE orders SET collected_amount = collected_amount + ? WHERE id = ? AND collected_amount + ? <= delivered_amount")) {
            ps.setInt(1, amount); ps.setLong(2, id); ps.setInt(3, amount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public synchronized boolean deleteOrder(long id) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM orders WHERE id = ?")) {
            ps.setLong(1, id); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public synchronized int countAll() {
        try (ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM orders")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) { return 0; }
    }

    public synchronized void recordDelivery(UUID deliverer, long payout, long spent) {
        incrementStat(deliverer, "deliveries", 1);
        incrementStat(deliverer, "earned", payout);
        if (spent > 0) incrementStat(deliverer, "spent", spent);
    }

    public synchronized PlayerStats stats(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM player_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new PlayerStats(0, 0, 0, 0);
                return new PlayerStats(rs.getInt("placed"), rs.getInt("deliveries"), rs.getLong("earned"), rs.getLong("spent"));
            }
        } catch (SQLException e) { return new PlayerStats(0, 0, 0, 0); }
    }

    private void incrementStat(UUID uuid, String column, long delta) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO player_stats (uuid, placed, deliveries, earned, spent) VALUES (?,0,0,0,0) ON CONFLICT(uuid) DO NOTHING")) {
            ps.setString(1, uuid.toString()); ps.executeUpdate();
        } catch (SQLException ignored) {}
        try (PreparedStatement ps = connection.prepareStatement("UPDATE player_stats SET " + column + " = " + column + " + ? WHERE uuid = ?")) {
            ps.setLong(1, delta); ps.setString(2, uuid.toString()); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private static Order readOrder(ResultSet rs) throws SQLException {
        return new Order(rs.getLong("id"), UUID.fromString(rs.getString("owner_uuid")), rs.getBytes("item_bytes"),
                rs.getInt("total_amount"), rs.getInt("delivered_amount"), rs.getInt("collected_amount"),
                rs.getLong("price_per_item"), rs.getLong("created_at"), rs.getLong("expires_at"));
    }

    public synchronized void close() {
        if (connection != null) { try { connection.close(); } catch (SQLException ignored) {} connection = null; }
    }
}

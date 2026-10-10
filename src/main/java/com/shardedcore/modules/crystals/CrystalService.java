package com.shardedcore.modules.crystals;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.util.Amounts;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class CrystalService {

    private final ShardedCore plugin;
    private final Sqlite sqlite;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final double starting;

    public CrystalService(ShardedCore plugin, Sqlite sqlite, double starting) {
        this.plugin = plugin;
        this.sqlite = sqlite;
        this.starting = starting;
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS crystals (
                        uuid TEXT PRIMARY KEY,
                        balance REAL NOT NULL
                    )
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create crystals table", ex);
        }
    }

    public double get(UUID uuid) {
        return balances.computeIfAbsent(uuid, this::load);
    }

    public void set(UUID uuid, double amount) {
        double value = Math.max(0, amount);
        balances.put(uuid, value);
        save(uuid);
    }

    public boolean add(UUID uuid, double amount) {
        if (amount < 0) return false;
        set(uuid, get(uuid) + amount);
        return true;
    }

    public boolean take(UUID uuid, double amount) {
        if (get(uuid) < amount) return false;
        set(uuid, get(uuid) - amount);
        return true;
    }

    public String format(double amount) {
        return Amounts.format(amount);
    }

    private double load(UUID uuid) {
        try {
            Double value = sqlite.query("SELECT balance FROM crystals WHERE uuid = ?", rs -> {
                try {
                    return rs.next() ? rs.getDouble("balance") : null;
                } catch (SQLException ex) {
                    return null;
                }
            }, uuid.toString());
            return value == null ? starting : value;
        } catch (SQLException ex) {
            return starting;
        }
    }

    private void save(UUID uuid) {
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("""
                        INSERT INTO crystals (uuid, balance) VALUES (?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance
                        """, uuid.toString(), get(uuid));
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save crystals for " + uuid, ex);
            }
        });
    }
}

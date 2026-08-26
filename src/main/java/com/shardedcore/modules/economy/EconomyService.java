package com.shardedcore.modules.economy;

import com.shardedcore.data.Toggles;
import com.shardedcore.database.Sqlite;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class EconomyService {

    private final ShardedCoreEconomy owner;
    private final Sqlite sqlite;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> frozen = new ConcurrentHashMap<>();
    private final double starting;

    public interface ShardedCoreEconomy {
        org.bukkit.plugin.Plugin plugin();
        Toggles toggles();
        void log(Level level, String message, Throwable error);
    }

    public EconomyService(com.shardedcore.ShardedCore plugin, Sqlite sqlite, double starting) {
        this.owner = new ShardedCoreEconomy() {
            @Override
            public org.bukkit.plugin.Plugin plugin() {
                return plugin;
            }

            @Override
            public Toggles toggles() {
                return plugin.toggles();
            }

            @Override
            public void log(Level level, String message, Throwable error) {
                plugin.getLogger().log(level, message, error);
            }
        };
        this.sqlite = sqlite;
        this.starting = starting;
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS economy (
                        uuid TEXT PRIMARY KEY,
                        balance REAL NOT NULL,
                        frozen INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        } catch (SQLException ex) {
            owner.log(Level.SEVERE, "Failed to create economy table", ex);
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
        if (frozen(uuid)) return false;
        set(uuid, get(uuid) + amount);
        return true;
    }

    public boolean take(UUID uuid, double amount) {
        if (frozen(uuid) || get(uuid) < amount) return false;
        set(uuid, get(uuid) - amount);
        return true;
    }

    public boolean frozen(UUID uuid) {
        return frozen.computeIfAbsent(uuid, id -> {
            try {
                Boolean value = sqlite.query("SELECT frozen FROM economy WHERE uuid = ?", rs -> {
                    try {
                        return rs.next() && rs.getInt("frozen") == 1;
                    } catch (SQLException ex) {
                        return false;
                    }
                }, id.toString());
                return Boolean.TRUE.equals(value);
            } catch (SQLException ex) {
                return false;
            }
        });
    }

    public void freeze(UUID uuid, boolean value) {
        frozen.put(uuid, value);
        save(uuid);
    }

    public String format(double amount) {
        return com.shardedcore.util.Amounts.format(amount);
    }

    private double load(UUID uuid) {
        try {
            Double value = sqlite.query("SELECT balance FROM economy WHERE uuid = ?", rs -> {
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
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(owner.plugin(), () -> {
            try {
                sqlite.execute("""
                        INSERT INTO economy (uuid, balance, frozen) VALUES (?, ?, ?)
                        ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance, frozen = excluded.frozen
                        """, uuid.toString(), get(uuid), frozen(uuid) ? 1 : 0);
            } catch (SQLException ex) {
                owner.log(Level.SEVERE, "Failed to save economy for " + uuid, ex);
            }
        });
    }
}

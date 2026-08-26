package com.shardedcore.modules.economy;

import com.shardedcore.ShardedCore;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EconomyService {

    private record CacheEntry(long balance, boolean frozen, boolean exists) {
    }

    private record PendingWrite(UUID uuid, Long balance, Boolean frozen) {
    }

    private final ShardedCore plugin;
    private final EconomyDatabase database;
    private final long startingBalance;
    private final long maxBalance;
    private final Map<UUID, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<UUID, PendingWrite> pending = new ConcurrentHashMap<>();
    private volatile int flushTaskId = -1;
    private static final long FLUSH_DELAY_TICKS = 40L;

    public EconomyService(ShardedCore plugin, EconomyDatabase database, long startingBalance, long maxBalance) {
        this.plugin = plugin;
        this.database = database;
        this.startingBalance = Math.max(0L, startingBalance);
        this.maxBalance = Math.max(0L, maxBalance);
    }

    public long getBalance(UUID uuid) {
        CacheEntry cached = cache.get(uuid);
        if (cached != null) {
            return cached.balance();
        }
        synchronized (this) {
            cached = cache.get(uuid);
            if (cached != null) {
                return cached.balance();
            }
            EconomyDatabase.AccountRow row = database.getAccount(uuid);
            if (!row.exists()) {
                putCache(uuid, new CacheEntry(startingBalance, false, true));
                queueWrite(uuid, startingBalance, false);
                return startingBalance;
            }
            putCache(uuid, new CacheEntry(row.balance(), row.frozen(), true));
            return row.balance();
        }
    }

    public void setBalance(UUID uuid, long balance) {
        long capped = cap(balance);
        putCache(uuid, new CacheEntry(capped, isFrozen(uuid), true));
        queueWrite(uuid, capped, null);
    }

    public void add(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean take(UUID uuid, long amount) {
        if (amount <= 0) {
            return true;
        }
        long current = getBalance(uuid);
        if (current < amount) {
            return false;
        }
        setBalance(uuid, current - amount);
        return true;
    }

    public void reset(UUID uuid) {
        setBalance(uuid, startingBalance);
    }

    public boolean isFrozen(UUID uuid) {
        CacheEntry cached = cache.get(uuid);
        if (cached != null) {
            return cached.frozen();
        }
        synchronized (this) {
            cached = cache.get(uuid);
            if (cached != null) {
                return cached.frozen();
            }
            EconomyDatabase.AccountRow row = database.getAccount(uuid);
            if (!row.exists()) {
                putCache(uuid, new CacheEntry(startingBalance, false, true));
                queueWrite(uuid, startingBalance, false);
                return false;
            }
            putCache(uuid, new CacheEntry(row.balance(), row.frozen(), true));
            return row.frozen();
        }
    }

    public void setFrozen(UUID uuid, boolean frozen) {
        putCache(uuid, new CacheEntry(getBalance(uuid), frozen, true));
        queueWrite(uuid, null, frozen);
    }

    public boolean canReceive(UUID uuid, long amount) {
        if (maxBalance <= 0) {
            return true;
        }
        return getBalance(uuid) + amount <= maxBalance;
    }

    public void saveNow() {
        flushPendingSync();
    }

    public void close() {
        flushPendingSync();
        cache.clear();
    }

    private long cap(long balance) {
        long value = Math.max(0L, balance);
        if (maxBalance > 0 && value > maxBalance) {
            return maxBalance;
        }
        return value;
    }

    private void putCache(UUID uuid, CacheEntry entry) {
        cache.put(uuid, entry);
    }

    private void queueWrite(UUID uuid, Long balance, Boolean frozen) {
        pending.merge(uuid, new PendingWrite(uuid, balance, frozen), (existing, incoming) -> new PendingWrite(
                uuid,
                incoming.balance() != null ? incoming.balance() : existing.balance(),
                incoming.frozen() != null ? incoming.frozen() : existing.frozen()
        ));
        scheduleFlush();
    }

    private void scheduleFlush() {
        if (flushTaskId >= 0) {
            return;
        }
        flushTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            flushTaskId = -1;
            flushPendingAsync();
        }, FLUSH_DELAY_TICKS).getTaskId();
    }

    private void flushPendingAsync() {
        if (pending.isEmpty()) {
            return;
        }
        Map<UUID, PendingWrite> batch = Map.copyOf(pending);
        pending.keySet().removeAll(batch.keySet());
        synchronized (this) {
            upsertBatch(batch);
        }
    }

    private void flushPendingSync() {
        if (pending.isEmpty()) {
            return;
        }
        Map<UUID, PendingWrite> batch = Map.copyOf(pending);
        pending.clear();
        if (flushTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        synchronized (this) {
            upsertBatch(batch);
        }
    }

    private void upsertBatch(Map<UUID, PendingWrite> batch) {
        for (PendingWrite write : batch.values()) {
            CacheEntry cached = cache.get(write.uuid());
            Long balance = write.balance();
            Boolean frozen = write.frozen();
            if (balance == null && cached != null) {
                balance = cached.balance();
            }
            if (frozen == null && cached != null) {
                frozen = cached.frozen();
            }
            database.upsert(write.uuid(), balance, frozen);
        }
    }
}

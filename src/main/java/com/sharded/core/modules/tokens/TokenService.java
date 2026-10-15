package com.sharded.core.modules.tokens;

import com.sharded.core.util.OfflinePlayers;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

public final class TokenService {

    private final TokenDatabase database;

    public TokenService(TokenDatabase database) {
        this.database = database;
    }

    public long getBalance(UUID uuid) {
        return database.getBalance(uuid);
    }

    public void setBalance(UUID uuid, long amount) {
        database.setBalance(uuid, amount);
    }

    public void give(UUID uuid, long amount) {
        if (amount <= 0) return;
        database.setBalance(uuid, getBalance(uuid) + amount);
    }

    public boolean take(UUID uuid, long amount) {
        if (amount <= 0) return true;
        long current = getBalance(uuid);
        if (current < amount) return false;
        database.setBalance(uuid, current - amount);
        return true;
    }

    public void reset(UUID uuid) {
        database.setBalance(uuid, 0);
    }

    public OfflinePlayer resolve(String name) {
        return OfflinePlayers.resolve(name);
    }
}

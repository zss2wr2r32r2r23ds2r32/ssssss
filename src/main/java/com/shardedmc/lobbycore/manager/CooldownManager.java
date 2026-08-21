package com.shardedmc.lobbycore.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public boolean isOnCooldown(UUID uuid, String key) {
        Long expiry = cooldowns.get(uuid + ":" + key);
        if (expiry == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expiry) {
            cooldowns.remove(uuid + ":" + key);
            return false;
        }
        return true;
    }

    public long getRemainingSeconds(UUID uuid, String key) {
        Long expiry = cooldowns.get(uuid + ":" + key);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (remaining / 1000) + 1;
    }

    public void setCooldown(UUID uuid, String key, long seconds) {
        cooldowns.put(uuid + ":" + key, System.currentTimeMillis() + (seconds * 1000));
    }

    public void clearCooldown(UUID uuid, String key) {
        cooldowns.remove(uuid + ":" + key);
    }
}

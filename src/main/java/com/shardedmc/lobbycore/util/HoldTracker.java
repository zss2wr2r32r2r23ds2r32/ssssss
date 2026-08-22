package com.shardedmc.lobbycore.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class HoldTracker {

    private int slot = -1;
    private long startTime;
    private boolean active;

    public void update(Player player) {
        int held = player.getInventory().getHeldItemSlot();
        if (held != slot) {
            slot = held;
            startTime = System.currentTimeMillis();
            active = true;
        }
    }

    public void reset() {
        slot = -1;
        startTime = 0;
        active = false;
    }

    public boolean hasHeldFor(long millis) {
        return active && (System.currentTimeMillis() - startTime) >= millis;
    }

    public long getHeldMillis() {
        return active ? System.currentTimeMillis() - startTime : 0;
    }

    public int getSlot() {
        return slot;
    }
}

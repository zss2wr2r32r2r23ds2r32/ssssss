package com.sharded.core.modules.graves;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runtime data for a single grave. */
public final class Grave {

    public final UUID id;
    public final UUID owner;
    public final String ownerName;
    public final Location location;
    public final List<ItemStack> items;
    public int xp;
    public boolean xpClaimed;
    public final long createdAt;
    public final long expiresAt;

    /** Spawned marker entity (player head armor stand). */
    public UUID markerEntityId;

    /** Spawned TextDisplay hologram entity ids (name, timer, xp). */
    public final List<UUID> hologramIds = new ArrayList<>();
    public boolean hologramsSpawned;

    public Grave(UUID id, UUID owner, String ownerName, Location location,
                 List<ItemStack> items, int xp, boolean xpClaimed, long createdAt, long expiresAt) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.location = location;
        this.items = items;
        this.xp = xp;
        this.xpClaimed = xpClaimed;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public long secondsLeft() {
        return Math.max(0L, (expiresAt - System.currentTimeMillis()) / 1000L);
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}

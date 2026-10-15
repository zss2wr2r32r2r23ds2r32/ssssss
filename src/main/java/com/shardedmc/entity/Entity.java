package com.shardedmc.entity;

import com.shardedmc.world.ChunkPos;

import java.util.UUID;

/**
 * Base entity with position tracking and tick lifecycle.
 */
public final class Entity {

    private final UUID id;
    private final EntityType type;
    private volatile double x;
    private volatile double y;
    private volatile double z;
    private volatile boolean nearPlayer;
    private volatile long lastTick;

    public Entity(UUID id, EntityType type, double x, double y, double z) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.nearPlayer = true;
    }

    public UUID getId() {
        return id;
    }

    public EntityType getType() {
        return type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public ChunkPos getChunkPos() {
        return new ChunkPos((int) Math.floor(x / 16.0), (int) Math.floor(z / 16.0));
    }

    public boolean isNearPlayer() {
        return nearPlayer;
    }

    public void setNearPlayer(boolean nearPlayer) {
        this.nearPlayer = nearPlayer;
    }

    public void tick(long currentTick) {
        this.lastTick = currentTick;
        // Physics/AI simulation placeholder
    }

    public long getLastTick() {
        return lastTick;
    }
}

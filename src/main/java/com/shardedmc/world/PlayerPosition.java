package com.shardedmc.world;

import java.util.UUID;

/**
 * Tracks a player's world position for scheduling prioritization.
 */
public record PlayerPosition(
        UUID id,
        double x,
        double z,
        boolean moving,
        long lastUpdateMs
) {
}

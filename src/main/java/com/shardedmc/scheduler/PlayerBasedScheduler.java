package com.shardedmc.scheduler;

import com.shardedmc.world.ChunkPos;
import com.shardedmc.world.PlayerPosition;

import java.util.*;

/**
 * Player-aware chunk and task prioritization to prevent workload starvation.
 */
public final class PlayerBasedScheduler {

    private final Map<UUID, PlayerPosition> players = new HashMap<>();
    private final PriorityQueue<ChunkLoadRequest> loadQueue = new PriorityQueue<>();

    public void updatePlayer(UUID id, double x, double z, boolean moving) {
        players.put(id, new PlayerPosition(id, x, z, moving, System.currentTimeMillis()));
        reprioritizeAround(x, z, moving ? 10 : 5);
    }

    public void removePlayer(UUID id) {
        players.remove(id);
    }

    public void enqueueLoad(int chunkX, int chunkZ, int basePriority) {
        double minDist = players.values().stream()
                .mapToDouble(p -> distanceToChunk(p, chunkX, chunkZ))
                .min()
                .orElse(Double.MAX_VALUE);

        int priority = basePriority;
        if (minDist < 3) {
            priority += 20;
        } else if (minDist < 8) {
            priority += 10;
        } else if (minDist > 32) {
            priority -= 5;
        }

        loadQueue.offer(new ChunkLoadRequest(chunkX, chunkZ, priority));
    }

    public Optional<ChunkLoadRequest> pollHighestPriority() {
        return loadQueue.isEmpty() ? Optional.empty() : Optional.of(loadQueue.poll());
    }

    public Collection<PlayerPosition> getPlayers() {
        return Collections.unmodifiableCollection(players.values());
    }

    public int getQueueSize() {
        return loadQueue.size();
    }

    private void reprioritizeAround(double x, double z, int radius) {
        int centerX = (int) Math.floor(x / 16.0);
        int centerZ = (int) Math.floor(z / 16.0);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                enqueueLoad(centerX + dx, centerZ + dz, 15 - Math.abs(dx) - Math.abs(dz));
            }
        }
    }

    private static double distanceToChunk(PlayerPosition player, int chunkX, int chunkZ) {
        double pcx = player.x() / 16.0;
        double pcz = player.z() / 16.0;
        return Math.hypot(pcx - chunkX, pcz - chunkZ);
    }

    public record ChunkLoadRequest(int chunkX, int chunkZ, int priority)
            implements Comparable<ChunkLoadRequest> {

        public ChunkPos pos() {
            return new ChunkPos(chunkX, chunkZ);
        }

        @Override
        public int compareTo(ChunkLoadRequest other) {
            return Integer.compare(other.priority, this.priority);
        }
    }
}

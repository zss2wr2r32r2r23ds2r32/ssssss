package com.shardedmc.entity;

import java.util.*;

/**
 * Spatial hash grid for efficient entity proximity queries.
 */
public final class SpatialIndex {

    private final int cellSize;
    private final Map<Long, Set<Entity>> cells = new HashMap<>();

    public SpatialIndex(int cellSize) {
        this.cellSize = cellSize;
    }

    public void insert(Entity entity) {
        long key = cellKey(entity.getX(), entity.getZ());
        cells.computeIfAbsent(key, k -> new HashSet<>()).add(entity);
    }

    public void remove(Entity entity) {
        long key = cellKey(entity.getX(), entity.getZ());
        Set<Entity> set = cells.get(key);
        if (set != null) {
            set.remove(entity);
            if (set.isEmpty()) {
                cells.remove(key);
            }
        }
    }

    public void update(Entity entity) {
        remove(entity);
        insert(entity);
    }

    public Collection<Entity> query(double x, double y, double z, double radius) {
        int minCx = (int) Math.floor((x - radius) / cellSize);
        int maxCx = (int) Math.floor((x + radius) / cellSize);
        int minCz = (int) Math.floor((z - radius) / cellSize);
        int maxCz = (int) Math.floor((z + radius) / cellSize);

        List<Entity> results = new ArrayList<>();
        double radiusSq = radius * radius;

        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Set<Entity> set = cells.get(packCell(cx, cz));
                if (set == null) {
                    continue;
                }
                for (Entity entity : set) {
                    double dx = entity.getX() - x;
                    double dy = entity.getY() - y;
                    double dz = entity.getZ() - z;
                    if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                        results.add(entity);
                    }
                }
            }
        }
        return results;
    }

    public void clear() {
        cells.clear();
    }

    private long cellKey(double x, double z) {
        return packCell((int) Math.floor(x / cellSize), (int) Math.floor(z / cellSize));
    }

    private static long packCell(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }
}

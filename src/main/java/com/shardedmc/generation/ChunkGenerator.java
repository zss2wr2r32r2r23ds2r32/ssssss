package com.shardedmc.generation;

import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.world.Chunk;
import com.shardedmc.world.ChunkPos;

import java.util.Random;

/**
 * Parallel terrain generation with efficient noise calculations.
 */
public final class ChunkGenerator {

    private final long seed;
    private final NoiseCache noiseCache;

    public ChunkGenerator(ShardedMCConfig config) {
        this.seed = 0x5DEECE66DL;
        this.noiseCache = new NoiseCache(256);
    }

    public void generate(Chunk chunk) {
        ChunkPos pos = chunk.getPos();
        Random random = new Random(seed ^ (pos.x() * 341873128712L + pos.z() * 132897987541L));

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = pos.x() * 16 + x;
                int worldZ = pos.z() * 16 + z;
                double height = noiseCache.sample(worldX, worldZ, random);
                int surface = (int) (64 + height * 32);

                for (int y = 0; y < surface - 4; y++) {
                    chunk.setBlock(x, y, z, (short) 1); // stone
                }
                for (int y = Math.max(0, surface - 4); y < surface; y++) {
                    chunk.setBlock(x, y, z, (short) 2); // dirt
                }
                if (surface < 256) {
                    chunk.setBlock(x, surface, z, (short) 3); // grass
                }
            }
        }
        chunk.setState(Chunk.State.LIGHTING);
    }
}

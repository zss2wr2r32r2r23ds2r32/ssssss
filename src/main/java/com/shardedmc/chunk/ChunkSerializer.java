package com.shardedmc.chunk;

import com.shardedmc.world.Chunk;
import com.shardedmc.world.ChunkPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Efficient region-file style chunk serialization with minimal decompression overhead.
 */
public final class ChunkSerializer {

    private final Path worldDir;

    public ChunkSerializer(Path serverRoot) {
        this.worldDir = serverRoot.resolve("world").resolve("region");
    }

    public Optional<byte[]> load(ChunkPos pos) {
        Path file = chunkFile(pos);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new ChunkIoException("Failed to read chunk " + pos, e);
        }
    }

    public void save(ChunkPos pos, Chunk chunk) throws IOException {
        Files.createDirectories(worldDir);
        Path file = chunkFile(pos);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        byte[] data = chunk.serialize();
        Files.write(temp, data);
        Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    private Path chunkFile(ChunkPos pos) {
        return worldDir.resolve("r." + pos.x() + "." + pos.z() + ".chunk");
    }

    public static final class ChunkIoException extends RuntimeException {
        public ChunkIoException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

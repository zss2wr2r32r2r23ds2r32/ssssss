package com.shardedmc.network;

/**
 * Lightweight network packet representation.
 */
public final class NetworkPacket {

    private final int id;
    private final byte[] payload;
    private int compressionLevel = 3;
    private int priority = 0;

    public NetworkPacket(int id, byte[] payload) {
        this.id = id;
        this.payload = payload;
    }

    public int getId() {
        return id;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }
}

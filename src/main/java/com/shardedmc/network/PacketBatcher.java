package com.shardedmc.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Batches network packets to reduce syscall overhead and improve throughput.
 */
public final class PacketBatcher {

    private final int maxBatchSize;
    private final List<NetworkPacket> batch = new ArrayList<>();
    private final Queue<NetworkPacket> pending = new ConcurrentLinkedQueue<>();

    public PacketBatcher(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public void offer(NetworkPacket packet) {
        pending.offer(packet);
    }

    public void flush(Queue<NetworkPacket> outbound) {
        NetworkPacket packet;
        while ((packet = pending.poll()) != null) {
            batch.add(packet);
            if (batch.size() >= maxBatchSize) {
                sendBatch(outbound);
            }
        }
        if (!batch.isEmpty()) {
            sendBatch(outbound);
        }
    }

    private void sendBatch(Queue<NetworkPacket> outbound) {
        // Adaptive compression based on batch size
        int compressionLevel = batch.size() > maxBatchSize / 2 ? 6 : 3;
        for (NetworkPacket packet : batch) {
            packet.setCompressionLevel(compressionLevel);
            outbound.offer(packet);
        }
        batch.clear();
    }
}

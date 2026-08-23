package com.shardedmc.network;

import com.shardedmc.config.ShardedMCConfig;
import com.shardedmc.diagnostics.PerformanceMonitor;
import com.shardedmc.scheduler.ServerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Asynchronous network processing with packet batching and compression optimization.
 */
public final class NetworkManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkManager.class);

    private final ShardedMCConfig config;
    private final ServerScheduler scheduler;
    private final PerformanceMonitor performanceMonitor;
    private final PacketBatcher batcher;
    private final Queue<NetworkPacket> inboundQueue = new ConcurrentLinkedQueue<>();
    private final Queue<NetworkPacket> outboundQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger connectedPlayers = new AtomicInteger();
    private final AtomicLong packetsProcessed = new AtomicLong();

    private volatile boolean running;
    private int port;
    private int maxPlayers;

    public NetworkManager(
            ShardedMCConfig config,
            ServerScheduler scheduler,
            PerformanceMonitor performanceMonitor
    ) {
        this.config = config;
        this.scheduler = scheduler;
        this.performanceMonitor = performanceMonitor;
        this.batcher = new PacketBatcher(config.getNetwork().getMaxPacketBatchSize());
    }

    public void start(int port, int maxPlayers) {
        this.port = port;
        this.maxPlayers = maxPlayers;
        this.running = true;
        LOGGER.info("NetworkManager listening on port {} (max-players={}, async={})",
                port, maxPlayers, config.getNetwork().isAsyncProcessing());
    }

    public void processInbound() {
        if (!running) {
            return;
        }
        int batch = config.getNetwork().getMaxPacketBatchSize();
        for (int i = 0; i < batch; i++) {
            NetworkPacket packet = inboundQueue.poll();
            if (packet == null) {
                break;
            }
            if (config.getNetwork().isAsyncProcessing()) {
                scheduler.submit(ServerScheduler.PoolType.NETWORK, () -> handlePacket(packet));
            } else {
                handlePacket(packet);
            }
            packetsProcessed.incrementAndGet();
        }
    }

    public void processOutbound() {
        batcher.flush(outboundQueue);
    }

    private void handlePacket(NetworkPacket packet) {
        // Protocol handling placeholder — real implementation would decode Minecraft packets
        performanceMonitor.recordNetworkLoad(1);
    }

    public void send(NetworkPacket packet) {
        outboundQueue.offer(packet);
        batcher.offer(packet);
    }

    public void enqueueInbound(NetworkPacket packet) {
        inboundQueue.offer(packet);
    }

    public int getConnectedPlayers() {
        return connectedPlayers.get();
    }

    public int getInboundQueueSize() {
        return inboundQueue.size();
    }

    public int getOutboundQueueSize() {
        return outboundQueue.size();
    }

    public long getPacketsProcessed() {
        return packetsProcessed.get();
    }

    public void shutdown() {
        running = false;
        inboundQueue.clear();
        outboundQueue.clear();
        LOGGER.info("NetworkManager shut down");
    }
}

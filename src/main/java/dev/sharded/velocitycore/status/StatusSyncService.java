package dev.sharded.velocitycore.status;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import dev.sharded.velocitycore.ServerState;
import dev.sharded.velocitycore.common.PluginChannels;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class StatusSyncService {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannels.STATUS_CHANNEL);

    private final ProxyServer server;
    private final ServerStatusManager statusManager;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "ShardedVelocityCore-status-sync");
        thread.setDaemon(true);
        return thread;
    });

    public StatusSyncService(ProxyServer server, ServerStatusManager statusManager) {
        this.server = server;
        this.statusManager = statusManager;
        server.getChannelRegistrar().register(CHANNEL);
    }

    public void start() {
        broadcast();
        executor.scheduleAtFixedRate(this::broadcast, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    public void sendToPlayer(Player player) {
        statusManager.refreshNow();
        player.sendPluginMessage(CHANNEL, encode(statusManager.snapshot()));
    }

    private void broadcast() {
        statusManager.refreshNow();
        byte[] payload = encode(statusManager.snapshot());
        for (RegisteredServer registeredServer : server.getAllServers()) {
            registeredServer.sendPluginMessage(CHANNEL, payload);
        }
        for (Player player : server.getAllPlayers()) {
            player.sendPluginMessage(CHANNEL, payload);
        }
    }

    static byte[] encode(Map<String, ServerState> snapshot) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeInt(snapshot.size());
        snapshot.forEach((server, state) -> {
            writeString(output, server);
            writeString(output, state.name());
            writeString(output, state.display());
        });
        return output.toByteArray();
    }

    private static void writeString(ByteArrayDataOutput output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}

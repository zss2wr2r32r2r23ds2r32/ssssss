package dev.sharded.velocitycore.status;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.sharded.velocitycore.ServerState;
import dev.sharded.velocitycore.common.PluginChannels;
import dev.sharded.velocitycore.config.PluginConfig;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class StatusSyncService {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannels.STATUS_CHANNEL);

    private final ProxyServer server;
    private final ServerStatusManager statusManager;
    private final PluginConfig config;
    private ScheduledTask syncTask;

    public StatusSyncService(ProxyServer server, ServerStatusManager statusManager, PluginConfig config) {
        this.server = server;
        this.statusManager = statusManager;
        this.config = config;
        server.getChannelRegistrar().register(CHANNEL);
    }

    public void start(Object plugin) {
        broadcastNow();
        long intervalMs = config.statusSyncIntervalSeconds() * 1000L;
        syncTask = server.getScheduler()
                .buildTask(plugin, this::broadcastNow)
                .repeat(intervalMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .schedule();
    }

    public void stop() {
        if (syncTask != null) {
            syncTask.cancel();
        }
    }

    public void sendToPlayer(Player player) {
        statusManager.refreshNow();
        player.sendPluginMessage(CHANNEL, encode(statusManager.snapshot()));
    }

    public void broadcastNow() {
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

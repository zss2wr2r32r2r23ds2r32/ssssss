package dev.sharded.velocitycore.lobby.sync;

import dev.sharded.velocitycore.lobby.hologram.HologramRefreshService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class StatusSyncListener implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final StatusCache cache;
    private final HologramRefreshService hologramRefreshService;
    private volatile int lastPayloadHash;

    public StatusSyncListener(
            JavaPlugin plugin,
            StatusCache cache,
            HologramRefreshService hologramRefreshService
    ) {
        this.plugin = plugin;
        this.cache = cache;
        this.hologramRefreshService = hologramRefreshService;
    }

    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        int hash = Arrays.hashCode(message);
        if (hash == lastPayloadHash) {
            return;
        }

        try {
            StatusCache.Snapshot snapshot = decode(message);
            if (!cache.update(snapshot)) {
                lastPayloadHash = hash;
                return;
            }
            lastPayloadHash = hash;
            Bukkit.getScheduler().runTask(plugin, hologramRefreshService::refreshAll);
        } catch (IOException ignored) {
            // Ignore malformed status sync payloads.
        }
    }

    static StatusCache.Snapshot decode(byte[] message) throws IOException {
        Map<String, StatusCache.Entry> entries = new HashMap<>();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(message));
        int count = input.readInt();
        for (int i = 0; i < count; i++) {
            String server = readString(input).toLowerCase(Locale.ROOT);
            readString(input);
            String display = readString(input);
            entries.put(server, new StatusCache.Entry(display));
        }
        return new StatusCache.Snapshot(entries);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        byte[] bytes = input.readNBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

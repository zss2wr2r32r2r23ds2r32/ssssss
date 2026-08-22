package dev.sharded.velocitycore.placeholder.sync;

import dev.sharded.velocitycore.common.ServerState;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class StatusSyncListener implements PluginMessageListener {

    private final StatusCache cache;

    public StatusSyncListener(StatusCache cache) {
        this.cache = cache;
    }

    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        try {
            cache.update(decode(message));
        } catch (IOException exception) {
            // Ignore malformed payloads
        }
    }

    static StatusCache.Snapshot decode(byte[] message) throws IOException {
        StatusCache.Snapshot.Builder builder = StatusCache.snapshotBuilder();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(message));
        int count = input.readInt();
        for (int i = 0; i < count; i++) {
            String server = readString(input).toLowerCase(Locale.ROOT);
            String stateName = readString(input);
            String display = readString(input);
            ServerState state;
            try {
                state = ServerState.valueOf(stateName);
            } catch (IllegalArgumentException ignored) {
                state = ServerState.OFFLINE;
            }
            builder.put(server, state, display);
        }
        return builder.build();
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        byte[] bytes = input.readNBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

package dev.sharded.velocitycore.lobby.sync;

import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class StatusSyncListener implements PluginMessageListener {

    private final StatusCache cache;

    public StatusSyncListener(StatusCache cache) {
        this.cache = cache;
    }

    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        try {
            cache.update(decode(message));
        } catch (IOException ignored) {
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

package dev.sharded.velocitycore.lobby.maintenance;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.sharded.velocitycore.lobby.config.LobbySettings;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;

public final class MaintenanceSyncService {

    public static final String CHANNEL = "shardedvelocitycore:maintenance";

    private final JavaPlugin plugin;
    private final LobbySettings settings;

    public MaintenanceSyncService(JavaPlugin plugin, LobbySettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    public void register() {
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void syncNow(boolean enabled) {
        if (!Bukkit.getServer().getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL)) {
            return;
        }

        byte[] payload = encode(
                enabled,
                settings.maintenanceMotd(),
                settings.serverListVersionText(),
                settings.serverListProtocolVersion()
        );
        Bukkit.getServer().sendPluginMessage(plugin, CHANNEL, payload);
    }

    static byte[] encode(boolean enabled, String maintenanceMotd, String versionText, int protocolVersion) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeBoolean(enabled);
        writeString(output, maintenanceMotd);
        writeString(output, versionText);
        output.writeInt(protocolVersion);
        return output.toByteArray();
    }

    private static void writeString(ByteArrayDataOutput output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}

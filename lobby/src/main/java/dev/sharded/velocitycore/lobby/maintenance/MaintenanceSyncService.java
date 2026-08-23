package dev.sharded.velocitycore.lobby.maintenance;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.sharded.velocitycore.lobby.motd.MotdService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MaintenanceSyncService {

    public static final String CHANNEL = "shardedvelocitycore:maintenance";
    private static final byte PROTOCOL_VERSION = 2;

    private final JavaPlugin plugin;
    private final MotdService motdService;

    public MaintenanceSyncService(JavaPlugin plugin, MotdService motdService) {
        this.plugin = plugin;
        this.motdService = motdService;
    }

    public void register() {
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
    }

    public void syncNow(boolean maintenanceEnabled) {
        if (!Bukkit.getServer().getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL)) {
            return;
        }

        MotdService.ResolvedMotd normal = motdService.resolveDefault();
        MotdService.ResolvedMotd maintenance = motdService.resolveMaintenance();
        byte[] payload = encode(maintenanceEnabled, normal, maintenance);
        Bukkit.getServer().sendPluginMessage(plugin, CHANNEL, payload);
    }

    static byte[] encode(boolean maintenanceEnabled, MotdService.ResolvedMotd normal, MotdService.ResolvedMotd maintenance) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeByte(PROTOCOL_VERSION);
        output.writeBoolean(maintenanceEnabled);
        writeLines(output, normal.rawLines());
        writeString(output, normal.icon());
        writeLines(output, maintenance.rawLines());
        writeString(output, maintenance.icon());
        writeString(output, maintenance.versionText());
        output.writeInt(maintenance.protocolVersion());
        return output.toByteArray();
    }

    private static void writeLines(ByteArrayDataOutput output, List<String> lines) {
        output.writeInt(lines.size());
        for (String line : lines) {
            writeString(output, line);
        }
    }

    private static void writeString(ByteArrayDataOutput output, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }
}

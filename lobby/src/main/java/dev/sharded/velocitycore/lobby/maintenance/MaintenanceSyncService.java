package dev.sharded.velocitycore.lobby.maintenance;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.sharded.velocitycore.lobby.motd.MotdService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class MaintenanceSyncService implements PluginMessageListener {

    public static final String CHANNEL = "shardedvelocitycore:maintenance";
    private static final byte PROTOCOL_VERSION = 3;
    private static final byte REQUEST = 0x01;

    private final JavaPlugin plugin;
    private final MotdService motdService;
    private MaintenanceManager maintenanceManager;

    public MaintenanceSyncService(JavaPlugin plugin, MotdService motdService) {
        this.plugin = plugin;
        this.motdService = motdService;
    }

    public void register(MaintenanceManager maintenanceManager) {
        this.maintenanceManager = maintenanceManager;
        Bukkit.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        Bukkit.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void syncNow(boolean maintenanceEnabled) {
        if (!Bukkit.getServer().getMessenger().isOutgoingChannelRegistered(plugin, CHANNEL)) {
            return;
        }

        byte[] payload = encode(maintenanceEnabled, motdService.resolveDefault());
        Bukkit.getServer().sendPluginMessage(plugin, CHANNEL, payload);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || maintenanceManager == null) {
            return;
        }
        if (message != null && message.length == 1 && message[0] == REQUEST) {
            syncNow(maintenanceManager.isEnabled());
        }
    }

    private byte[] encode(boolean maintenanceEnabled, MotdService.ResolvedMotd normal) {
        ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeByte(PROTOCOL_VERSION);
        output.writeBoolean(maintenanceEnabled);
        writeLines(output, normal.rawLines());
        writeString(output, normal.icon());
        writeString(output, motdService.config().protocolTextValue());
        output.writeInt(motdService.config().protocolVersion());
        output.writeBoolean(motdService.config().hoverEnabled());
        writeLines(output, motdService.config().hoverMessages());
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

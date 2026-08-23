package dev.sharded.velocitycore.lobby.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class LobbySettings {

    private final String motd;
    private final List<String> kickMessageLines;
    private final String maintenanceMotd;
    private final String serverListVersionText;
    private final int serverListProtocolVersion;

    public LobbySettings(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        motd = config.getString("motd", "&#8AFF00&lSHARDEDMC");
        List<String> configuredKickLines = config.getStringList("maintenance.kick-message");
        if (configuredKickLines.isEmpty()) {
            kickMessageLines = List.of(
                    "&#FF0000&lMAINTENANCE",
                    "&fThis server is currently in downtime",
                    "",
                    "&fIf you believe the is an error contact staff via",
                    "&#FFD900▷ &n&ldiscord.gg/shardedmc&f &#FFD900◁"
            );
        } else {
            kickMessageLines = new ArrayList<>(configuredKickLines);
        }
        maintenanceMotd = config.getString("maintenance.maintenance-motd", "&#FF0000&lMAINTENANCE");
        serverListVersionText = config.getString("maintenance.server-list.version-text", "Maintenance");
        serverListProtocolVersion = config.getInt("maintenance.server-list.protocol-version", -1);
    }

    public String motd() {
        return motd;
    }

    public List<String> kickMessageLines() {
        return kickMessageLines;
    }

    public String kickMessage() {
        return String.join("\n", kickMessageLines);
    }

    public String maintenanceMotd() {
        return maintenanceMotd;
    }

    public String serverListVersionText() {
        return serverListVersionText;
    }

    public int serverListProtocolVersion() {
        return serverListProtocolVersion;
    }
}

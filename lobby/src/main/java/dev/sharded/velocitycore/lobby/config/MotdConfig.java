package dev.sharded.velocitycore.lobby.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MotdConfig {

    private final List<String> defaultLines;
    private final int centerWidth;
    private final boolean serverIconEnabled;
    private final String serverIconImage;
    private final boolean hoverEnabled;
    private final List<String> hoverMessages;
    private final List<String> kickMessageLines;
    private final boolean protocolText;
    private final String protocolTextValue;
    private final int protocolVersion;

    public MotdConfig(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        centerWidth = config.getInt("motd.center-width", 48);
        defaultLines = centerLines(readLines(config, "motd.lines", List.of(
                "&#8AFF00&lSHARDEDMC &8▷ &7[1.21+]",
                "&#FFE300⚓ &#FFE300&lSEASON 1 SOON &#FFE300⚓"
        )));

        serverIconEnabled = config.getBoolean("server-icon.enabled", false);
        serverIconImage = config.getString("server-icon.image", "default-server-icon.png");

        hoverEnabled = config.getBoolean("hover.enabled", true);
        hoverMessages = readLines(config, "hover.messages", List.of(
                "&d&lSHARDEDMC NETWORK &8| &7 1.21.11",
                "",
                "&#A183CD🔥 ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ:",
                "&7▶ &fDiscord: &dᴅɪsᴄᴏʀᴅ.ɢɢ/shardedmc",
                "&7▶ &fStore: &dᴄᴏᴍɪɴɢ sᴏᴏɴ",
                "&fplay with {online_players} other players"
        ));

        kickMessageLines = readLines(config, "maintenance-motd.kick-message", List.of(
                "&#FF0000&lMAINTENANCE",
                "&fThis server is currently in downtime"
        ));
        protocolText = config.getBoolean("maintenance-motd.protocol-text", true);
        protocolTextValue = config.getString("maintenance-motd.text", "Maintenance");
        protocolVersion = config.getInt("maintenance-motd.protocol-version", -1);
    }

    private List<String> centerLines(List<String> lines) {
        List<String> centered = new ArrayList<>(lines.size());
        for (String line : lines) {
            centered.add(MotdCenter.center(line, centerWidth));
        }
        return centered;
    }

    public List<String> defaultLines() {
        return defaultLines;
    }

    public boolean serverIconEnabled() {
        return serverIconEnabled;
    }

    public String serverIconImage() {
        return serverIconImage;
    }

    public boolean hoverEnabled() {
        return hoverEnabled;
    }

    public List<String> hoverMessages() {
        return hoverMessages;
    }

    public List<String> kickMessageLines() {
        return kickMessageLines;
    }

    public boolean protocolText() {
        return protocolText;
    }

    public String protocolTextValue() {
        return protocolTextValue;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    private static List<String> readLines(FileConfiguration config, String path, List<String> fallback) {
        List<String> lines = config.getStringList(path);
        return lines.isEmpty() ? fallback : new ArrayList<>(lines);
    }
}

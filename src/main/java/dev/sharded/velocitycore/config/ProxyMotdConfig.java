package dev.sharded.velocitycore.config;

import org.slf4j.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProxyMotdConfig {

    private final List<String> motdLines;
    private final boolean hoverEnabled;
    private final List<String> hoverMessages;
    private final String maintenanceVersionText;
    private final int maintenanceProtocolVersion;

    private ProxyMotdConfig(
            List<String> motdLines,
            boolean hoverEnabled,
            List<String> hoverMessages,
            String maintenanceVersionText,
            int maintenanceProtocolVersion
    ) {
        this.motdLines = motdLines;
        this.hoverEnabled = hoverEnabled;
        this.hoverMessages = hoverMessages;
        this.maintenanceVersionText = maintenanceVersionText;
        this.maintenanceProtocolVersion = maintenanceProtocolVersion;
    }

    public static ProxyMotdConfig load(Path configPath, Logger logger) {
        List<String> defaultMotd = centerDefaults(List.of(
                "&#AD4EFF&lSHARDEDMC &8▷ &7[1.21+]",
                "&#FFE300⚓ &#FFE300&lSEASON 1 SOON &#FFE300⚓"
        ));
        List<String> defaultHover = List.of(
                "&d&lSHARDEDMC NETWORK &8| &7 1.21.11",
                "",
                "&#A183CD🔥 ɪɴꜰᴏʀᴍᴀᴛɪᴏɴ:",
                "&7▶ &fDiscord: &dᴅɪsᴄᴏʀᴅ.ɢɢ/shardedmc",
                "&7▶ &fStore: &dᴄᴏᴍɪɴɢ sᴏᴏɴ",
                "&fplay with {online_players} other players"
        );

        if (!configPath.toFile().exists()) {
            return defaults(defaultMotd, defaultHover);
        }

        try {
            TomlParseResult parsed = Toml.parse(configPath);
            TomlTable motd = parsed.getTable("motd");
            TomlTable hover = parsed.getTable("hover");
            TomlTable maintenance = parsed.getTable("maintenance-motd");

            int centerWidth = motd != null && motd.getLong("center-width") != null
                    ? motd.getLong("center-width").intValue()
                    : 48;
            List<String> lines = readLines(motd, "lines", defaultMotd);
            List<String> centered = new ArrayList<>();
            for (String line : lines) {
                centered.add(MotdCenter.center(line, centerWidth));
            }

            boolean hoverEnabled = hover == null || hover.getBoolean("enabled") == null || hover.getBoolean("enabled");
            List<String> hoverMessages = readLines(hover, "messages", defaultHover);

            String versionText = maintenance != null && maintenance.getString("text") != null
                    ? maintenance.getString("text")
                    : "Maintenance";
            int protocolVersion = maintenance != null && maintenance.getLong("protocol-version") != null
                    ? maintenance.getLong("protocol-version").intValue()
                    : -1;

            return new ProxyMotdConfig(centered, hoverEnabled, hoverMessages, versionText, protocolVersion);
        } catch (IOException exception) {
            logger.warn("Failed to read proxy MOTD config, using defaults", exception);
            return defaults(defaultMotd, defaultHover);
        }
    }

    private static ProxyMotdConfig defaults(List<String> motdLines, List<String> hoverMessages) {
        return new ProxyMotdConfig(motdLines, true, hoverMessages, "Maintenance", -1);
    }

    private static List<String> centerDefaults(List<String> lines) {
        List<String> centered = new ArrayList<>();
        for (String line : lines) {
            centered.add(MotdCenter.center(line, 48));
        }
        return centered;
    }

    private static List<String> readLines(TomlTable table, String key, List<String> fallback) {
        if (table == null || table.getArray(key) == null) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        table.getArray(key).toList().forEach(value -> values.add(String.valueOf(value)));
        return values.isEmpty() ? fallback : values;
    }

    public List<String> motdLines() {
        return motdLines;
    }

    public boolean hoverEnabled() {
        return hoverEnabled;
    }

    public List<String> hoverMessages() {
        return hoverMessages;
    }

    public String maintenanceVersionText() {
        return maintenanceVersionText;
    }

    public int maintenanceProtocolVersion() {
        return maintenanceProtocolVersion;
    }
}

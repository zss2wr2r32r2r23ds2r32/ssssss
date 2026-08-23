package dev.sharded.velocitycore.lobby.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MotdConfig {

    private final List<String> defaultLines;
    private final boolean serverIconEnabled;
    private final String serverIconImage;
    private final boolean multiMotdEnabled;
    private final String multiMotdOrder;
    private final List<MotdEntry> multiMotds;
    private final List<String> kickMessageLines;
    private final String maintenanceOrder;
    private final boolean protocolText;
    private final String protocolTextValue;
    private final int protocolVersion;
    private final List<MotdEntry> maintenanceMotds;

    public MotdConfig(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        defaultLines = readLines(config, "motd.lines", List.of(
                "      §x§a§d§4§e§f§f§lSHARDEDMC <reset><gray>[1.21+] §8▶ §7.gg/shardedmc",
                "                 §x§8§A§F§F§0§0§lWELCOME"
        ));

        serverIconEnabled = config.getBoolean("server-icon.enabled", false);
        serverIconImage = config.getString("server-icon.image", "default-server-icon.png");

        multiMotdEnabled = config.getBoolean("multi-motd.enabled", false);
        multiMotdOrder = config.getString("multi-motd.order", "random");
        multiMotds = readMotdList(config.getList("multi-motd.motds"));

        kickMessageLines = readLines(config, "maintenance-motd.kick-message", List.of(
                "&#FF0000&lMAINTENANCE",
                "&fThis server is currently in downtime"
        ));
        maintenanceOrder = config.getString("maintenance-motd.order", "list");
        protocolText = config.getBoolean("maintenance-motd.protocol-text", true);
        protocolTextValue = config.getString("maintenance-motd.text", "&cMAINTENANCE");
        protocolVersion = config.getInt("maintenance-motd.protocol-version", -1);

        List<MotdEntry> loadedMaintenance = readMotdList(config.getList("maintenance-motd.motds"));
        if (loadedMaintenance.isEmpty()) {
            loadedMaintenance = List.of(new MotdEntry(
                    "      §x§a§d§4§e§f§f§lSHARDEDMC <reset><gray>[1.21+] §8▶ §7.gg/shardedmc",
                    "                 §x§F§F§0§0§0§0§lMAINTENANCE",
                    "maintenance.png"
            ));
        }
        maintenanceMotds = loadedMaintenance;
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

    public boolean multiMotdEnabled() {
        return multiMotdEnabled;
    }

    public String multiMotdOrder() {
        return multiMotdOrder;
    }

    public List<MotdEntry> multiMotds() {
        return multiMotds;
    }

    public List<String> kickMessageLines() {
        return kickMessageLines;
    }

    public String kickMessage() {
        return String.join("\n", kickMessageLines);
    }

    public String maintenanceOrder() {
        return maintenanceOrder;
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

    public List<MotdEntry> maintenanceMotds() {
        return maintenanceMotds;
    }

    private static List<String> readLines(FileConfiguration config, String path, List<String> fallback) {
        List<String> lines = config.getStringList(path);
        return lines.isEmpty() ? fallback : new ArrayList<>(lines);
    }

    @SuppressWarnings("unchecked")
    private static List<MotdEntry> readMotdList(List<?> rawList) {
        List<MotdEntry> entries = new ArrayList<>();
        if (rawList == null) {
            return entries;
        }
        for (Object raw : rawList) {
            if (raw instanceof Map<?, ?> map) {
                entries.add(new MotdEntry(
                        stringValue(map.get("line1")),
                        stringValue(map.get("line2")),
                        stringValue(map.get("icon"))
                ));
            } else if (raw instanceof ConfigurationSection section) {
                entries.add(new MotdEntry(
                        section.getString("line1", ""),
                        section.getString("line2", ""),
                        section.getString("icon", "")
                ));
            }
        }
        return entries;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record MotdEntry(String line1, String line2, String icon) {
        public List<String> lines() {
            List<String> lines = new ArrayList<>(2);
            if (line1 != null && !line1.isBlank()) {
                lines.add(line1);
            }
            if (line2 != null && !line2.isBlank()) {
                lines.add(line2);
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        public String iconOrEmpty() {
            return icon == null ? "" : icon;
        }
    }
}

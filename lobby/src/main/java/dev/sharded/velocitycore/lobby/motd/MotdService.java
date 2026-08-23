package dev.sharded.velocitycore.lobby.motd;

import dev.sharded.velocitycore.lobby.config.MotdConfig;
import dev.sharded.velocitycore.lobby.config.MotdSelector;
import dev.sharded.velocitycore.lobby.util.TextParser;
import net.kyori.adventure.text.Component;

import java.util.List;

public final class MotdService {

    private final MotdConfig config;
    private final MotdSelector defaultSelector = new MotdSelector();
    private final MotdSelector maintenanceSelector = new MotdSelector();

    public MotdService(MotdConfig config) {
        this.config = config;
    }

    public MotdConfig config() {
        return config;
    }

    public ResolvedMotd resolveDefault() {
        if (config.multiMotdEnabled() && !config.multiMotds().isEmpty()) {
            MotdConfig.MotdEntry entry = defaultSelector.selectEntry(
                    config.multiMotdOrder(),
                    config.multiMotds(),
                    fallbackEntry(config.defaultLines())
            );
            return new ResolvedMotd(
                    TextParser.parseLines(entry.lines()),
                    entry.lines(),
                    entry.iconOrEmpty(),
                    false,
                    "",
                    0
            );
        }

        List<String> lines = config.defaultLines();
        return new ResolvedMotd(
                TextParser.parseLines(lines),
                lines,
                config.serverIconEnabled() ? config.serverIconImage() : "",
                false,
                "",
                0
        );
    }

    public ResolvedMotd resolveMaintenance() {
        MotdConfig.MotdEntry entry = maintenanceSelector.selectEntry(
                config.maintenanceOrder(),
                config.maintenanceMotds(),
                fallbackEntry(List.of("&#FF0000&lMAINTENANCE"))
        );
        String versionText = config.protocolText() ? config.protocolTextValue() : "Maintenance";
        List<String> lines = entry.lines();
        return new ResolvedMotd(
                TextParser.parseLines(lines),
                lines,
                entry.iconOrEmpty().isBlank() && config.serverIconEnabled()
                        ? config.serverIconImage()
                        : entry.iconOrEmpty(),
                true,
                versionText,
                config.protocolVersion()
        );
    }

    public Component kickComponent() {
        return TextParser.parseLines(config.kickMessageLines());
    }

    private static MotdConfig.MotdEntry fallbackEntry(List<String> lines) {
        String line1 = lines.size() > 0 ? lines.get(0) : "";
        String line2 = lines.size() > 1 ? lines.get(1) : "";
        return new MotdConfig.MotdEntry(line1, line2, "");
    }

    public record ResolvedMotd(
            net.kyori.adventure.text.Component motd,
            List<String> rawLines,
            String icon,
            boolean maintenance,
            String versionText,
            int protocolVersion
    ) {
    }
}

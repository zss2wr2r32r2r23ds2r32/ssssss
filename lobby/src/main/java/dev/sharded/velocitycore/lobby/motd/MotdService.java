package dev.sharded.velocitycore.lobby.motd;

import dev.sharded.velocitycore.lobby.config.MotdConfig;
import dev.sharded.velocitycore.lobby.util.TextParser;
import net.kyori.adventure.text.Component;

import java.util.List;

public final class MotdService {

    private final MotdConfig config;

    public MotdService(MotdConfig config) {
        this.config = config;
    }

    public MotdConfig config() {
        return config;
    }

    public ResolvedMotd resolveDefault() {
        List<String> lines = config.defaultLines();
        return new ResolvedMotd(
                TextParser.parseLines(lines),
                lines,
                config.serverIconEnabled() ? config.serverIconImage() : "",
                false
        );
    }

    public ResolvedMotd resolveMaintenance() {
        List<String> lines = config.defaultLines();
        return new ResolvedMotd(
                TextParser.parseLines(lines),
                lines,
                "",
                true
        );
    }

    public Component kickComponent() {
        return TextParser.parseLines(config.kickMessageLines());
    }

    public record ResolvedMotd(
            Component motd,
            List<String> rawLines,
            String icon,
            boolean maintenance
    ) {
    }
}

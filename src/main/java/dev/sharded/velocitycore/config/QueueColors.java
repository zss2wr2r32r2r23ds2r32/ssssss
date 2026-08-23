package dev.sharded.velocitycore.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class QueueColors {

    private final String position;
    private final String server;
    private final String waiting;
    private final String success;
    private final String error;
    private final String accent;
    private final Map<String, String> serverColors;

    public QueueColors(
            String position,
            String server,
            String waiting,
            String success,
            String error,
            String accent,
            Map<String, String> serverColors
    ) {
        this.position = position;
        this.server = server;
        this.waiting = waiting;
        this.success = success;
        this.error = error;
        this.accent = accent;
        this.serverColors = serverColors;
    }

    public static QueueColors defaults() {
        Map<String, String> serverColors = new HashMap<>();
        serverColors.put("survival", "&#8AFF00");
        serverColors.put("events", "&#FFAA00");
        serverColors.put("diasmp", "&#4498DB");
        serverColors.put("lobby", "&#4498DB");
        return new QueueColors(
                "&#FFFFFF",
                "&#8AFF00",
                "&#AAAAAA",
                "&#8AFF00",
                "&#FF0000",
                "&#4498DB",
                serverColors
        );
    }

    public String position() {
        return position;
    }

    public String server() {
        return server;
    }

    public String waiting() {
        return waiting;
    }

    public String success() {
        return success;
    }

    public String error() {
        return error;
    }

    public String accent() {
        return accent;
    }

    public String serverColor(String serverName) {
        String normalized = serverName.toLowerCase(Locale.ROOT);
        if (normalized.equals("diamondsmp")) {
            normalized = "diasmp";
        }
        return serverColors.getOrDefault(normalized, server);
    }

    public Map<String, String> serverColors() {
        return Collections.unmodifiableMap(serverColors);
    }
}

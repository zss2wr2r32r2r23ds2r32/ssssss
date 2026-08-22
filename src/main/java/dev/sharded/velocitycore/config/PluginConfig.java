package dev.sharded.velocitycore.config;

import org.slf4j.Logger;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class PluginConfig {

    private final int statusRefreshSeconds;
    private final List<String> trackedServers;
    private final Set<String> maintenanceServers;
    private final String queueActionBar;
    private final String queuePrefix;
    private final String defaultQueueServer;
    private final int actionBarIntervalTicks;
    private final Map<String, Integer> maxPlayers;

    private PluginConfig(
            int statusRefreshSeconds,
            List<String> trackedServers,
            Set<String> maintenanceServers,
            String queueActionBar,
            String queuePrefix,
            String defaultQueueServer,
            int actionBarIntervalTicks,
            Map<String, Integer> maxPlayers
    ) {
        this.statusRefreshSeconds = statusRefreshSeconds;
        this.trackedServers = trackedServers;
        this.maintenanceServers = maintenanceServers;
        this.queueActionBar = queueActionBar;
        this.queuePrefix = queuePrefix;
        this.defaultQueueServer = defaultQueueServer;
        this.actionBarIntervalTicks = actionBarIntervalTicks;
        this.maxPlayers = maxPlayers;
    }

    public static PluginConfig load(Path dataDirectory, Logger logger) {
        Path configPath = dataDirectory.resolve("config.toml");

        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            if (!Files.exists(configPath)) {
                try (InputStream defaults = PluginConfig.class.getResourceAsStream("/config.toml")) {
                    if (defaults != null) {
                        Files.copy(defaults, configPath);
                    }
                }
            }
        } catch (IOException exception) {
            logger.warn("Unable to create default config.toml", exception);
        }

        try {
            TomlParseResult parsed = Toml.parse(configPath);
            if (parsed.hasErrors()) {
                parsed.errors().forEach(error -> logger.warn("Config parse issue: {}", error.toString()));
            }

            int refresh = parsed.getLong("status-refresh-seconds") != null
                    ? parsed.getLong("status-refresh-seconds").intValue()
                    : 5;
            List<String> tracked = readStringList(parsed.getArray("tracked-servers"), List.of("survival", "events", "diamondsmp"));
            Set<String> maintenance = new HashSet<>();
            for (String server : readStringList(parsed.getArray("maintenance-servers"), List.of())) {
                maintenance.add(server.toLowerCase(Locale.ROOT));
            }

            TomlTable queue = parsed.getTable("queue");
            String actionBar = queue != null && queue.getString("action-bar") != null
                    ? queue.getString("action-bar")
                    : defaultActionBar();
            String prefix = queue != null && queue.getString("prefix") != null
                    ? queue.getString("prefix")
                    : defaultPrefix();
            String defaultServer = queue != null && queue.getString("default-server") != null
                    ? queue.getString("default-server")
                    : "survival";
            int interval = queue != null && queue.getLong("action-bar-interval-ticks") != null
                    ? queue.getLong("action-bar-interval-ticks").intValue()
                    : 20;

            Map<String, Integer> maxPlayers = new HashMap<>();
            if (queue != null) {
                TomlTable maxTable = queue.getTable("max-players");
                if (maxTable != null) {
                    maxTable.keySet().forEach(key -> {
                        Long value = maxTable.getLong(key);
                        if (value != null) {
                            maxPlayers.put(key.toLowerCase(Locale.ROOT), value.intValue());
                        }
                    });
                }
            }

            return new PluginConfig(
                    Math.max(1, refresh),
                    tracked,
                    maintenance,
                    actionBar,
                    prefix,
                    defaultServer.toLowerCase(Locale.ROOT),
                    Math.max(1, interval),
                    maxPlayers
            );
        } catch (IOException exception) {
            logger.warn("Failed to read config.toml, using defaults", exception);
            return defaults();
        }
    }

    private static PluginConfig defaults() {
        Map<String, Integer> maxPlayers = new HashMap<>();
        maxPlayers.put("survival", 100);
        maxPlayers.put("events", 200);
        maxPlayers.put("diamondsmp", 150);
        return new PluginConfig(
                5,
                List.of("survival", "events", "diamondsmp"),
                Set.of(),
                defaultActionBar(),
                defaultPrefix(),
                "survival",
                20,
                maxPlayers
        );
    }

    private static String defaultActionBar() {
        return "#%numberinqueue% in queue to &n&#8AFF00%server%&r &7(Wating: %numberofpeoplewaitinginqueue%)";
    }

    private static String defaultPrefix() {
        return "&#4498DB&lQUEUE &8▷&r ";
    }

    private static List<String> readStringList(TomlArray array, List<String> fallback) {
        if (array == null) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        array.toList().forEach(value -> values.add(String.valueOf(value).toLowerCase(Locale.ROOT)));
        return values.isEmpty() ? fallback : values;
    }

    public int statusRefreshSeconds() {
        return statusRefreshSeconds;
    }

    public List<String> trackedServers() {
        return trackedServers;
    }

    public Set<String> maintenanceServers() {
        return maintenanceServers;
    }

    public String queueActionBar() {
        return queueActionBar;
    }

    public String queuePrefix() {
        return queuePrefix;
    }

    public String defaultQueueServer() {
        return defaultQueueServer;
    }

    public int actionBarIntervalTicks() {
        return actionBarIntervalTicks;
    }

    public int maxPlayers(String server) {
        return maxPlayers.getOrDefault(server.toLowerCase(Locale.ROOT), 100);
    }

    public Map<String, Integer> maxPlayers() {
        return Collections.unmodifiableMap(maxPlayers);
    }
}

package com.shardedmc.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Plugin manager providing Bukkit/Spigot-style plugin loading via ServiceLoader.
 */
public final class PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    private final Path pluginsDir;
    private final List<ShardedPlugin> plugins = new ArrayList<>();

    public PluginManager(Path pluginsDir) {
        this.pluginsDir = pluginsDir;
    }

    public void loadPlugins() {
        try {
            Files.createDirectories(pluginsDir);
        } catch (Exception e) {
            LOGGER.warn("Could not create plugins directory", e);
        }

        ServiceLoader<ShardedPlugin> loader = ServiceLoader.load(ShardedPlugin.class);
        for (ShardedPlugin plugin : loader) {
            try {
                plugin.onEnable();
                plugins.add(plugin);
                LOGGER.info("Enabled plugin: {}", plugin.getName());
            } catch (Exception e) {
                LOGGER.error("Failed to enable plugin: {}", plugin.getName(), e);
            }
        }
    }

    public void disableAll() {
        for (ShardedPlugin plugin : plugins) {
            try {
                plugin.onDisable();
            } catch (Exception e) {
                LOGGER.error("Failed to disable plugin: {}", plugin.getName(), e);
            }
        }
        plugins.clear();
    }

    public List<ShardedPlugin> getPlugins() {
        return List.copyOf(plugins);
    }
}

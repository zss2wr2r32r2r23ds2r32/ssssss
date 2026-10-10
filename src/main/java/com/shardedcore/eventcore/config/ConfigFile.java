package com.shardedcore.eventcore.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

/**
 * A single YAML file on disk, backed by the matching jar resource for defaults.
 *
 * <p>Missing keys are filled in from the bundled copy rather than overwriting
 * the operator's file, so upgrading the plugin never destroys customisation.</p>
 */
public final class ConfigFile {

    private final Plugin plugin;
    private final String resourcePath;
    private final File file;
    private FileConfiguration configuration;

    public ConfigFile(Plugin plugin, String resourcePath) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        load();
    }

    public void load() {
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().warning("Could not create directory " + parent.getAbsolutePath());
            }
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                try {
                    if (!file.createNewFile()) {
                        plugin.getLogger().warning("Could not create " + resourcePath);
                    }
                } catch (IOException exception) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create " + resourcePath, exception);
                }
            }
        }

        configuration = YamlConfiguration.loadConfiguration(file);

        InputStream bundled = plugin.getResource(resourcePath);
        if (bundled != null) {
            try (InputStreamReader reader = new InputStreamReader(bundled, StandardCharsets.UTF_8)) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(reader));
                configuration.options().copyDefaults(true);
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not read bundled defaults for " + resourcePath, exception);
            }
        }
    }

    public void save() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + resourcePath, exception);
        }
    }

    public void reload() {
        load();
    }

    public FileConfiguration raw() {
        return configuration;
    }

    public ConfigurationSection section(String path) {
        ConfigurationSection existing = configuration.getConfigurationSection(path);
        return existing != null ? existing : configuration.createSection(path);
    }

    public String name() {
        return resourcePath;
    }

    public File file() {
        return file;
    }
}

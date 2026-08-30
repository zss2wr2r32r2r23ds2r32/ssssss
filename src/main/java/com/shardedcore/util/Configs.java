package com.shardedcore.util;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;

public final class Configs {

    private Configs() {
    }

    public static FileConfiguration load(File file) {
        if (!file.exists()) return new YamlConfiguration();
        return YamlConfiguration.loadConfiguration(file);
    }

    public static void save(FileConfiguration config, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            config.save(file);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not save " + file.getPath(), ex);
        }
    }

    public static void saveDefault(JavaPlugin plugin, String resourcePath, File target) {
        if (target.exists()) return;
        copyResource(plugin, resourcePath, target);
    }

    public static void copyResource(JavaPlugin plugin, String resourcePath, File target) {
        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) {
            plugin.getLogger().warning("Missing resource: " + resourcePath);
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create folder for " + target.getPath());
        }
        try (stream) {
            Files.copy(stream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save resource " + resourcePath, ex);
        }
    }

    public static YamlConfiguration jarDefaults(JavaPlugin plugin, String resourcePath) {
        InputStream stream = plugin.getResource(resourcePath);
        if (stream == null) return new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            return new YamlConfiguration();
        }
    }
}

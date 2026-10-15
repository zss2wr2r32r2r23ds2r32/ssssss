package com.shardedcore.util;

import com.shardedcore.ShardedCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class ConfigSync {

    private ConfigSync() {
    }

    public static void syncMainConfig(ShardedCore plugin) {
        File file = new File(plugin.getDataFolder(), "config.yml");
        sync(plugin, file, "config.yml");
        plugin.reloadConfig();
    }

    public static YamlConfiguration load(ShardedCore plugin, File file, String resourcePath) {
        sync(plugin, file, resourcePath);
        if (!file.exists()) return new YamlConfiguration();
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupt config at " + file.getPath() + ", replacing from jar: " + e.getMessage());
            backup(file);
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, true);
            }
            return file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        }
    }

    public static void sync(ShardedCore plugin, File file, String resourcePath) {
        InputStream resource = plugin.getResource(resourcePath);
        if (resource == null) return;

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        YamlConfiguration defaults;
        try {
            defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().severe("Invalid default config in jar: " + resourcePath + " — " + e.getMessage());
            return;
        }
        int jarVersion = defaults.contains("config-version")
                ? defaults.getInt("config-version")
                : 0;

        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
            return;
        }

        YamlConfiguration disk;
        try {
            disk = YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Corrupt config " + file.getPath() + ", replacing from jar: " + e.getMessage());
            backup(file);
            plugin.saveResource(resourcePath, true);
            return;
        }

        if (jarVersion <= 0) {
            disk.setDefaults(defaults);
            disk.options().copyDefaults(true);
            saveQuietly(plugin, disk, file);
            return;
        }

        int diskVersion = disk.getInt("config-version", 0);
        if (diskVersion < jarVersion) {
            backup(file);
            if (shouldReplaceOnUpgrade(resourcePath)) {
                plugin.saveResource(resourcePath, true);
                return;
            }
            disk.setDefaults(defaults);
            disk.options().copyDefaults(true);
            disk.set("config-version", jarVersion);
            saveQuietly(plugin, disk, file);
            return;
        }

        disk.setDefaults(defaults);
        disk.options().copyDefaults(true);
        saveQuietly(plugin, disk, file);
    }

    private static boolean shouldReplaceOnUpgrade(String resourcePath) {
        if (resourcePath.endsWith("gui.yml") || resourcePath.endsWith("shop.yml")) return true;
        return resourcePath.contains("/menus/") && resourcePath.endsWith(".yml");
    }

    private static void saveQuietly(ShardedCore plugin, YamlConfiguration disk, File file) {
        try {
            disk.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save " + file.getPath() + ": " + e.getMessage());
        }
    }

    private static void backup(File file) {
        try {
            Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}

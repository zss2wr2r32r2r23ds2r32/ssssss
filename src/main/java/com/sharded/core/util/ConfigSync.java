package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Keeps disk configs in sync with jar defaults when {@code config-version} increases. */
public final class ConfigSync {

    /** Bump when bundled defaults change and should overwrite older server files. */
    public static final int VERSION = 6;

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
        int jarVersion = defaults.getInt("config-version", VERSION);

        if (!file.exists()) {
            plugin.saveResource(resourcePath, false);
            plugin.getLogger().info("Created default config: " + resourcePath);
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
        int diskVersion = disk.getInt("config-version", 0);
        if (diskVersion < jarVersion) {
            backup(file);
            plugin.saveResource(resourcePath, true);
            plugin.getLogger().info("Updated " + file.getName() + " (v" + diskVersion + " -> v" + jarVersion + ")");
            return;
        }

        disk.setDefaults(defaults);
        disk.options().copyDefaults(true);
        try {
            disk.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not merge defaults into " + file.getPath() + ": " + e.getMessage());
        }
    }

    public static int resetAll(ShardedCore plugin) {
        int count = 0;
        count += resetResource(plugin, "config.yml");
        count += resetResourcesIn(plugin, "modules");
        return count;
    }

    private static int resetResourcesIn(ShardedCore plugin, String folder) {
        int count = 0;
        count += resetIfExists(plugin, "modules/armortrims/config.yml");
        count += resetIfExists(plugin, "modules/armortrims/messages.yml");
        count += resetIfExists(plugin, "modules/autosmelt/config.yml");
        count += resetIfExists(plugin, "modules/autosmelt/messages.yml");
        count += resetIfExists(plugin, "modules/backpack/config.yml");
        count += resetIfExists(plugin, "modules/backpack/messages.yml");
        count += resetIfExists(plugin, "modules/bundles/config.yml");
        count += resetIfExists(plugin, "modules/staff/config.yml");
        count += resetIfExists(plugin, "modules/staff/messages.yml");
        count += resetIfExists(plugin, "modules/client/config.yml");
        count += resetIfExists(plugin, "modules/client/messages.yml");
        count += resetIfExists(plugin, "modules/chat/config.yml");
        count += resetIfExists(plugin, "modules/chat/messages.yml");
        count += resetIfExists(plugin, "modules/craft/config.yml");
        count += resetIfExists(plugin, "modules/craft/messages.yml");
        count += resetIfExists(plugin, "modules/deathmessages/config.yml");
        count += resetIfExists(plugin, "modules/deathmessages/messages.yml");
        count += resetIfExists(plugin, "modules/fix/config.yml");
        count += resetIfExists(plugin, "modules/fix/messages.yml");
        count += resetIfExists(plugin, "modules/fly/config.yml");
        count += resetIfExists(plugin, "modules/fly/messages.yml");
        count += resetIfExists(plugin, "modules/graves/config.yml");
        count += resetIfExists(plugin, "modules/graves/messages.yml");
        count += resetIfExists(plugin, "modules/joinmessages/config.yml");
        count += resetIfExists(plugin, "modules/joinmessages/messages.yml");
        count += resetIfExists(plugin, "modules/kill/config.yml");
        count += resetIfExists(plugin, "modules/kill/messages.yml");
        count += resetIfExists(plugin, "modules/killstreaks/config.yml");
        count += resetIfExists(plugin, "modules/killstreaks/messages.yml");
        count += resetIfExists(plugin, "modules/nightvision/config.yml");
        count += resetIfExists(plugin, "modules/nightvision/messages.yml");
        count += resetIfExists(plugin, "modules/pets/config.yml");
        count += resetIfExists(plugin, "modules/pets/messages.yml");
        count += resetIfExists(plugin, "modules/pets/gui.yml");
        count += resetIfExists(plugin, "modules/pickupmobs/config.yml");
        count += resetIfExists(plugin, "modules/pickupmobs/messages.yml");
        count += resetIfExists(plugin, "modules/pickupspawners/config.yml");
        count += resetIfExists(plugin, "modules/pickupspawners/messages.yml");
        count += resetIfExists(plugin, "modules/portalrtp/config.yml");
        count += resetIfExists(plugin, "modules/portalrtp/messages.yml");
        count += resetIfExists(plugin, "modules/portalrtp/gui.yml");
        count += resetIfExists(plugin, "modules/spawnselect/config.yml");
        count += resetIfExists(plugin, "modules/spawnselect/messages.yml");
        count += resetIfExists(plugin, "modules/spawnselect/gui.yml");
        count += resetIfExists(plugin, "modules/eglow/config.yml");
        count += resetIfExists(plugin, "modules/eglow/messages.yml");
        count += resetIfExists(plugin, "modules/chatcolor/config.yml");
        count += resetIfExists(plugin, "modules/chatcolor/messages.yml");
        count += resetIfExists(plugin, "modules/namecolor/config.yml");
        count += resetIfExists(plugin, "modules/namecolor/messages.yml");
        count += resetIfExists(plugin, "modules/wardrobe/config.yml");
        count += resetIfExists(plugin, "modules/wardrobe/messages.yml");
        count += resetIfExists(plugin, "modules/tags/config.yml");
        count += resetIfExists(plugin, "modules/tags/messages.yml");
        count += resetIfExists(plugin, "modules/privatemessages/config.yml");
        count += resetIfExists(plugin, "modules/privatemessages/messages.yml");
        count += resetIfExists(plugin, "modules/settings/config.yml");
        count += resetIfExists(plugin, "modules/settings/messages.yml");
        count += resetIfExists(plugin, "modules/settings/gui.yml");
        count += resetIfExists(plugin, "modules/abilities/config.yml");
        count += resetIfExists(plugin, "modules/abilities/messages.yml");
        count += resetIfExists(plugin, "modules/abilities/shop.yml");
        count += resetIfExists(plugin, "modules/tempranks/config.yml");
        count += resetIfExists(plugin, "modules/tempranks/messages.yml");
        count += resetIfExists(plugin, "modules/tempranks/tempranks.yml");
        count += resetIfExists(plugin, "modules/tokens/config.yml");
        count += resetIfExists(plugin, "modules/tokens/messages.yml");
        for (String menu : new String[]{"mainmenu", "glow", "keys", "cosmetics", "gradients", "chatcolors", "tags", "backpack"}) {
            count += resetIfExists(plugin, "modules/tokens/menus/" + menu + ".yml");
        }
        count += resetIfExists(plugin, "modules/toolname/config.yml");
        count += resetIfExists(plugin, "modules/toolname/messages.yml");
        count += resetIfExists(plugin, "modules/trash/config.yml");
        count += resetIfExists(plugin, "modules/trash/messages.yml");
        count += resetIfExists(plugin, "modules/staff/config.yml");
        count += resetIfExists(plugin, "modules/staff/messages.yml");
        count += resetIfExists(plugin, "modules/client/config.yml");
        count += resetIfExists(plugin, "modules/client/messages.yml");
        return count;
    }

    private static int resetResource(ShardedCore plugin, String resourcePath) {
        if (plugin.getResource(resourcePath) == null) return 0;
        File file = new File(plugin.getDataFolder(), resourcePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        if (file.exists()) backup(file);
        plugin.saveResource(resourcePath, true);
        return 1;
    }

    private static int resetIfExists(ShardedCore plugin, String resourcePath) {
        if (plugin.getResource(resourcePath) == null) return 0;
        return resetResource(plugin, resourcePath);
    }

    private static void backup(File file) {
        try {
            Files.copy(file.toPath(), new File(file.getParentFile(), file.getName() + ".bak").toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
        }
    }
}

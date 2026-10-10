package com.shardedmc.lobbycore.manager;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final ShardedLobbyCore plugin;
    private FileConfiguration messagesConfig;
    private final Map<String, FileConfiguration> moduleConfigs = new HashMap<>();

    private static final String[] MODULE_FILES = {
            "default-items",
            "server-selector",
            "player-visibility",
            "parkour",
            "music",
            "pvp",
            "bow-popper",
            "double-jump",
            "launch-pads",
            "join-messages",
            "announcements",
            "chat-prefixes",
            "void-spawn",
            "spawn",
            "join-actions",
            "chat-filter",
            "command-whitelist",
            "ranks",
            "anti-swear",
            "moderation",
            "world-protection"
    };

    public ConfigManager(ShardedLobbyCore plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loadMessages();
        for (String module : MODULE_FILES) {
            loadModuleConfig(module);
        }
    }

    private void loadMessages() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(file);
        InputStream def = plugin.getResource("messages.yml");
        if (def != null) {
            messagesConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
    }

    private void loadModuleConfig(String moduleId) {
        File folder = new File(plugin.getDataFolder(), "modules");
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create modules folder.");
        }

        File file = new File(folder, moduleId + ".yml");
        if (!file.exists()) {
            plugin.saveResource("modules/" + moduleId + ".yml", false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        InputStream def = plugin.getResource("modules/" + moduleId + ".yml");
        if (def != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(def, StandardCharsets.UTF_8)));
        }
        moduleConfigs.put(moduleId, config);
    }

    public FileConfiguration getMessages() {
        return messagesConfig;
    }

    public FileConfiguration getModuleConfig(String moduleId) {
        return moduleConfigs.get(moduleId);
    }

    public void saveModuleConfig(String moduleId) {
        File file = new File(plugin.getDataFolder(), "modules/" + moduleId + ".yml");
        FileConfiguration config = moduleConfigs.get(moduleId);
        if (config != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save module config: " + moduleId);
            }
        }
    }
}

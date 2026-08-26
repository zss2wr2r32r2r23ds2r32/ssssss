package com.shardedcore.module;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.ConfigUtil;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public abstract class Module {

    protected final ShardedCore plugin;
    protected final String id;
    protected File moduleFolder;
    protected FileConfiguration config;
    protected FileConfiguration messages;

    protected Module(ShardedCore plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public void loadFiles() {
        moduleFolder = new File(plugin.getDataFolder(), "modules/" + id);
        if (!moduleFolder.exists() && !moduleFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create module folder for " + id);
        }

        File configFile = new File(moduleFolder, "config.yml");
        ConfigUtil.saveDefaultResource(plugin, "modules/" + id + "/config.yml", configFile, false);
        config = ConfigUtil.loadYaml(configFile);

        File messagesFile = new File(moduleFolder, "messages.yml");
        ConfigUtil.saveDefaultResource(plugin, "modules/" + id + "/messages.yml", messagesFile, false);
        messages = ConfigUtil.loadYaml(messagesFile);
    }

    public boolean isEnabledInConfig() {
        return plugin.isModuleEnabled(id);
    }

    public abstract void enable();

    public abstract void disable();

    public void reload() {
        loadFiles();
    }
}

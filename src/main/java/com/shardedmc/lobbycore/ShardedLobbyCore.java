package com.shardedmc.lobbycore;

import com.shardedmc.lobbycore.manager.ConfigManager;
import com.shardedmc.lobbycore.manager.CooldownManager;
import com.shardedmc.lobbycore.manager.ModuleManager;
import com.shardedmc.lobbycore.manager.SpawnManager;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedLobbyCore extends JavaPlugin {

    private static ShardedLobbyCore instance;
    private ConfigManager configManager;
    private ModuleManager moduleManager;
    private SpawnManager spawnManager;
    private CooldownManager cooldownManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.configManager = new ConfigManager(this);
        this.spawnManager = new SpawnManager(this);
        this.cooldownManager = new CooldownManager();
        this.moduleManager = new ModuleManager(this);

        MessageUtil.init(this);
        configManager.loadAll();
        spawnManager.load();
        moduleManager.loadModules();

        getLogger().info("ShardedLobbyCore v" + getDescription().getVersion() + " enabled with " + moduleManager.getEnabledCount() + " modules.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.unloadModules();
        }
        getLogger().info("ShardedLobbyCore disabled.");
    }

    public void reload() {
        reloadConfig();
        configManager.loadAll();
        spawnManager.load();
        moduleManager.reloadModules();
    }

    public static ShardedLobbyCore getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public SpawnManager getSpawnManager() {
        return spawnManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
}

package com.shardedmc.lobbycore.manager;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.command.MainCommand;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.module.impl.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

public class ModuleManager {

    private final ShardedLobbyCore plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Map<String, FileConfiguration> moduleConfigs = new HashMap<>();

    public ModuleManager(ShardedLobbyCore plugin) {
        this.plugin = plugin;
        registerModules();
    }

    private void registerModules() {
        register(new DefaultItemsModule());
        register(new ServerSelectorModule());
        register(new PlayerVisibilityModule());
        register(new ParkourModule());
        register(new PvpModule());
        register(new BowPopperModule());
        register(new DoubleJumpModule());
        register(new LaunchPadsModule());
        register(new JoinMessagesModule());
        register(new AnnouncementsModule());
        register(new ChatPrefixModule());
        register(new VoidSpawnModule());
        register(new SpawnModule());
        register(new JoinActionsModule());
        register(new CommandWhitelistModule());
        register(new AntiSwearModule());
        register(new ModerationModule());
        register(new WorldProtectionModule());
    }

    private void register(Module module) {
        modules.put(module.getId(), module);
    }

    public void loadModules() {
        Objects.requireNonNull(plugin.getCommand("shardedlobbycore")).setExecutor(new MainCommand(plugin));

        for (Module module : modules.values()) {
            FileConfiguration config = plugin.getConfigManager().getModuleConfig(module.getId());
            moduleConfigs.put(module.getId(), config);

            if (config.getBoolean("enabled", true)) {
                module.enable(plugin, config);
                plugin.getLogger().info("Enabled module: " + module.getDisplayName());
            } else {
                plugin.getLogger().info("Skipped module (disabled): " + module.getDisplayName());
            }
        }
    }

    public void unloadModules() {
        for (Module module : modules.values()) {
            try {
                module.disable();
            } catch (Exception e) {
                plugin.getLogger().warning("Error disabling module " + module.getId() + ": " + e.getMessage());
            }
        }
    }

    public void reloadModules() {
        unloadModules();
        loadModules();
    }

    public int getEnabledCount() {
        int count = 0;
        for (Module module : modules.values()) {
            FileConfiguration config = moduleConfigs.get(module.getId());
            if (config != null && config.getBoolean("enabled", true)) {
                count++;
            }
        }
        return count;
    }

    public Module getModule(String id) {
        return modules.get(id);
    }

    public Collection<Module> getModules() {
        return modules.values();
    }

    public FileConfiguration getModuleConfig(String id) {
        return moduleConfigs.get(id);
    }
}

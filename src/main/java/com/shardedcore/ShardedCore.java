package com.shardedcore;

import com.shardedcore.command.ShardedCoreCommand;
import com.shardedcore.command.StubCommand;
import com.shardedcore.module.ModuleManager;
import com.shardedcore.util.ConfigUtil;
import com.shardedcore.util.MessageUtil;
import com.shardedcore.util.PlayerStateStore;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.logging.Level;

public final class ShardedCore extends JavaPlugin {

    private static ShardedCore instance;

    private ModuleManager moduleManager;
    private PlayerStateStore stateStore;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadLocalConfig();

        stateStore = new PlayerStateStore(this);
        stateStore.init();

        registerCommands();

        moduleManager = new ModuleManager(this);
        moduleManager.loadAll();
        getLogger().info("ShardedCore enabled.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (stateStore != null) {
            stateStore.close();
        }
        instance = null;
        getLogger().info("ShardedCore disabled.");
    }

    public void reloadLocalConfig() {
        reloadConfig();
        config = getConfig();
        MessageUtil.reload(this);
    }

    public void reloadPlugin() {
        reloadLocalConfig();
        if (stateStore != null) {
            stateStore.reload();
        }
        if (moduleManager != null) {
            moduleManager.reloadAll();
        }
    }

    private void registerCommands() {
        bindCommand("shardedcore", new ShardedCoreCommand(this));

        StubCommand stub = new StubCommand(this);
        List<String> stubCommands = List.of(
                "trash", "spawn", "setspawn", "delspawn",
                "home", "homes", "sethome", "delhome",
                "tpa", "tpahere", "tpaccept", "tpacancel", "tpatoggle", "tpauto",
                "rules", "guide", "announce",
                "craft", "anvil", "grindstone", "smithingtable",
                "settings", "chattoggle", "msgtoggle", "jointoggle", "deathtoggle",
                "mobtoggle", "paytoggle", "nightvision",
                "cf", "order", "orderadmin",
                "sell", "worth", "sellmulti", "shop",
                "kits", "kit", "killrewards", "playtimerewards",
                "rtp", "team", "crate", "media", "joincounter", "tp"
        );
        for (String name : stubCommands) {
            bindCommand(name, stub);
        }
    }

    private void bindCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().log(Level.WARNING, "Command ''{0}'' is missing from plugin.yml", name);
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    public static ShardedCore getInstance() {
        return instance;
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public PlayerStateStore stateStore() {
        return stateStore;
    }

    public FileConfiguration pluginConfig() {
        return config;
    }

    public String prefix() {
        return config.getString("prefix", "<gray>[ShardedCore]</gray> ");
    }

    public MessageUtil.MessageMode messageMode() {
        return MessageUtil.messageMode(config.getString("message-mode", "chat"));
    }

    public boolean isModuleEnabled(String moduleId) {
        return config.getBoolean("modules." + moduleId, true);
    }

    public boolean hasPlaceholderApi() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public boolean hasLuckPerms() {
        return Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
    }
}

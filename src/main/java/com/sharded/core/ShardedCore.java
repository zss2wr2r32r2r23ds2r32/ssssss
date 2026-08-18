package com.sharded.core;

import com.sharded.core.hook.LuckPermsHook;
import com.sharded.core.module.ModuleManager;
import com.sharded.core.util.PlayerStateStore;
import com.sharded.core.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedCore extends JavaPlugin {

    private static ShardedCore instance;

    private LuckPermsHook luckPerms;
    private PlayerStateStore stateStore;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.luckPerms = new LuckPermsHook(this);
        this.stateStore = new PlayerStateStore(this);
        this.moduleManager = new ModuleManager(this);
        this.moduleManager.enableModules();

        getLogger().info("ShardedCore enabled with " + moduleManager.enabledCount() + " modules.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) moduleManager.disableModules();
        if (stateStore != null) stateStore.saveNow();
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("shardedcore")) return false;
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            moduleManager.reload();
            stateStore.saveNow();
            sender.sendMessage(Text.c("&8[&bShardedCore&8] &aConfiguration reloaded. &7(" + moduleManager.enabledCount() + " modules enabled)"));
            return true;
        }
        sender.sendMessage(Text.c("&8[&bShardedCore&8] &7Running &bShardedCore v" + getDescription().getVersion() + "&7. Use &f/shardedcore reload&7."));
        return true;
    }

    public static ShardedCore get() {
        return instance;
    }

    public LuckPermsHook luckPerms() {
        return luckPerms;
    }

    public PlayerStateStore stateStore() {
        return stateStore;
    }

    public ModuleManager modules() {
        return moduleManager;
    }
}

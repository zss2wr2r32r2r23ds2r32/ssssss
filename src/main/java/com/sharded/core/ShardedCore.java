package com.sharded.core;

import com.sharded.core.gui.GuiListener;
import com.sharded.core.gui.GuiManager;
import com.sharded.core.hook.LuckPermsHook;
import com.sharded.core.module.ModuleManager;
import com.sharded.core.util.PlayerStateStore;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class ShardedCore extends JavaPlugin implements TabCompleter {

    private static ShardedCore instance;

    private LuckPermsHook luckPerms;
    private PlayerStateStore stateStore;
    private GuiManager guiManager;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.luckPerms = new LuckPermsHook(this);
        this.stateStore = new PlayerStateStore(this);
        this.guiManager = new GuiManager(this);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);

        this.moduleManager = new ModuleManager(this);
        this.moduleManager.enableModules();

        var admin = getCommand("shardedcore");
        if (admin != null) admin.setTabCompleter(this);

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
            sender.sendMessage(Text.c(getConfig().getString("prefix", "&8[&bSharded&8] &r")
                    + "&aConfiguration reloaded. &7(" + moduleManager.enabledCount() + " modules enabled)"));
            return true;
        }
        sender.sendMessage(Text.c(getConfig().getString("prefix", "&8[&bSharded&8] &r")
                + "&7Running &bShardedCore v" + getDescription().getVersion() + "&7. Use &f/shardedcore reload&7."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("shardedcore")) return List.of();
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "reload");
        }
        return List.of();
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

    public GuiManager gui() {
        return guiManager;
    }

    public ModuleManager modules() {
        return moduleManager;
    }
}

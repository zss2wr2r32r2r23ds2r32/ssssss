package com.sharded.core;

import com.sharded.core.gui.GuiListener;
import com.sharded.core.gui.GuiManager;
import com.sharded.core.gui.GuiNavigation;
import com.sharded.core.hook.LuckPermsHook;
import com.sharded.core.hook.PlaceholderHook;
import com.sharded.core.module.ModuleManager;
import com.sharded.core.util.CommandHelp;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.CoreTabComplete;
import com.sharded.core.util.GuiSounds;
import com.sharded.core.util.MessageUtil;
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
    private PlaceholderHook placeholderHook;
    private PlayerStateStore stateStore;
    private GuiManager guiManager;
    private GuiNavigation guiNavigation;
    private GuiSounds guiSounds;
    private ModuleManager moduleManager;
    private CoreTabComplete coreTabComplete;

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        saveDefaultConfig();
        ConfigSync.syncMainConfig(this);

        this.luckPerms = new LuckPermsHook(this);
        this.placeholderHook = new PlaceholderHook(this);
        getServer().getPluginManager().registerEvents(placeholderHook, this);
        this.stateStore = new PlayerStateStore(this);
        this.guiNavigation = new GuiNavigation(this);
        this.guiSounds = new GuiSounds(this);
        this.guiManager = new GuiManager(this);
        this.coreTabComplete = new CoreTabComplete(this);
        getServer().getPluginManager().registerEvents(new GuiListener(guiManager), this);

        this.moduleManager = new ModuleManager(this);
        this.moduleManager.enableModules();
        placeholderHook.tryRegister();

        var admin = getCommand("shardedcore");
        if (admin != null) admin.setTabCompleter(this);

        getLogger().info("ShardedCore enabled with " + moduleManager.enabledCount() + " modules.");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) moduleManager.disableModules();
        if (stateStore != null) {
            stateStore.saveNow();
            stateStore.close();
        }
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("shardedcore")) return false;
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("sharded.admin")) {
                MessageUtil.deliver(sender, getConfig().getString("prefix", "&8[&bSharded&8] &r") + "&cYou don't have permission.",
                        globalDelivery());
                return true;
            }
            ConfigSync.syncMainConfig(this);
            if (guiNavigation != null) guiNavigation.reload(this);
            if (guiSounds != null) guiSounds.reload();
            moduleManager.reload();
            stateStore.saveNow();
            MessageUtil.deliver(sender, getConfig().getString("prefix", "&8[&bSharded&8] &r")
                            + "&aConfiguration reloaded. &7(" + moduleManager.enabledCount() + " modules enabled)",
                    globalDelivery());
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("resetconfigs")) {
            if (!sender.hasPermission("sharded.admin")) {
                MessageUtil.deliver(sender, getConfig().getString("prefix", "&8[&bSharded&8] &r") + "&cYou don't have permission.",
                        globalDelivery());
                return true;
            }
            int count = ConfigSync.resetAll(this);
            reloadConfig();
            moduleManager.reload();
            MessageUtil.deliver(sender, getConfig().getString("prefix", "&8[&bSharded&8] &r")
                            + "&aReset &f" + count + " &aconfig files from plugin defaults.",
                    globalDelivery());
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("staff")) {
            CommandHelp.sendStaff(sender, getConfig().getString("prefix", "&8[&bSharded&8] &r"));
            return true;
        }
        CommandHelp.send(sender, getConfig().getString("prefix", "&8[&bSharded&8] &r"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("shardedcore")) return List.of();
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "reload", "resetconfigs", "staff", "help");
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

    public GuiNavigation guiNavigation() {
        return guiNavigation;
    }

    public GuiSounds guiSounds() {
        return guiSounds;
    }

    public ModuleManager modules() {
        return moduleManager;
    }

    public CoreTabComplete coreTabComplete() {
        return coreTabComplete;
    }

    public MessageUtil.Delivery globalDelivery() {
        MessageUtil.Delivery mode = MessageUtil.Delivery.parse(getConfig().getString("message-mode", "chat"));
        return mode != null ? mode : MessageUtil.Delivery.CHAT;
    }
}

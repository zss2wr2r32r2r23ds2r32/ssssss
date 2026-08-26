package com.shardedcore;

import com.shardedcore.command.CoreCommand;
import com.shardedcore.command.DisabledCommand;
import com.shardedcore.data.Toggles;
import com.shardedcore.gui.Menus;
import com.shardedcore.hook.CoreExpansion;
import com.shardedcore.module.Module;
import com.shardedcore.module.ModuleManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShardedCore extends JavaPlugin {

    private static ShardedCore instance;
    private ModuleManager modules;
    private Toggles toggles;
    private Menus menus;
    private DisabledCommand disabledCommands;
    private CoreExpansion expansion;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        disabledCommands = new DisabledCommand(this);
        bindAllDisabled();

        toggles = new Toggles(this);
        toggles.init();
        menus = new Menus(this);
        menus.register();

        PluginCommand core = getCommand("shardedcore");
        CoreCommand executor = new CoreCommand(this);
        if (core != null) {
            core.setExecutor(executor);
            core.setTabCompleter(executor);
        }
        PluginCommand modulesCommand = getCommand("modules");
        if (modulesCommand != null) {
            modulesCommand.setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("shardedcore.admin")) {
                    sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                            .deserialize("§cNo permission."));
                    return true;
                }
                if (sender instanceof Player player) {
                    modules.openGui(player, 0);
                } else {
                    sender.sendMessage("Use /shardedcore modules");
                }
                return true;
            });
        }

        modules = new ModuleManager(this);
        modules.loadAll();
        registerExpansion();
        getLogger().info("ShardedCore enabled.");
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (modules != null) modules.disableAll();
        if (toggles != null) toggles.close();
        instance = null;
        getLogger().info("ShardedCore disabled.");
    }

    public void reloadPlugin() {
        reloadConfig();
        if (modules != null) modules.loadAll();
        Bukkit.getOnlinePlayers().forEach(Player::updateCommands);
    }

    private void bindAllDisabled() {
        if (getDescription().getCommands() == null) return;
        for (String name : getDescription().getCommands().keySet()) {
            if (name.equals("shardedcore") || name.equals("modules")) continue;
            PluginCommand command = getCommand(name);
            if (command == null) continue;
            command.setPermission(Module.DISABLED_PERMISSION);
            command.setExecutor(disabledCommands);
            command.setTabCompleter(disabledCommands);
        }
    }

    private void registerExpansion() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        expansion = new CoreExpansion(this);
        expansion.register();
    }

    public static ShardedCore get() {
        return instance;
    }

    public ModuleManager modules() {
        return modules;
    }

    public Toggles toggles() {
        return toggles;
    }

    public Menus menus() {
        return menus;
    }

    public DisabledCommand disabledCommands() {
        return disabledCommands;
    }

    public boolean moduleEnabled(String id) {
        return getConfig().getBoolean("modules." + id, true);
    }

    public String prefix() {
        return getConfig().getString("prefix", "&#A370EE&lCORE &8▷ &r");
    }
}

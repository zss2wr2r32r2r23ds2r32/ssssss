package com.shardedcore.module;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.ConfigUtil;
import com.shardedcore.util.MessageUtil;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {

    protected final ShardedCore plugin;
    protected final String id;
    protected File moduleFolder;
    protected FileConfiguration config;
    protected FileConfiguration messages;
    private final List<String> boundCommands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    protected Module(ShardedCore plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public String getId() {
        return id;
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

    protected void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("[" + id + "] Command '" + name + "' is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
        boundCommands.add(name);
    }

    protected void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    protected void cleanup() {
        clearCommands();
        for (Listener listener : listeners) {
            HandlerList.unregisterAll(listener);
        }
        listeners.clear();
    }

    protected void clearCommands() {
        for (String name : boundCommands) {
            PluginCommand command = plugin.getCommand(name);
            if (command != null) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        }
        boundCommands.clear();
    }

    protected String messagePrefix() {
        if (config != null && config.isString("prefix")) {
            return config.getString("prefix");
        }
        if (messages != null && messages.isString("prefix")) {
            return messages.getString("prefix").replace("%prefix%", plugin.prefix());
        }
        return plugin.prefix();
    }

    protected String raw(String key, String... replacements) {
        String message = messages.getString(key, "<missing:" + id + "/" + key + ">");
        message = message.replace("%prefix%", messagePrefix());
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return message;
    }

    public FileConfiguration config() {
        return config;
    }

    public FileConfiguration messages() {
        return messages;
    }

    public void sendMessage(CommandSender to, String key, String... replacements) {
        send(to, key, replacements);
    }

    protected void send(CommandSender to, String key, String... replacements) {
        String message = raw(key, replacements);
        if (message.isEmpty()) {
            return;
        }
        MessageUtil.sendRaw(to, message, to instanceof Player player ? player : null);
    }
}

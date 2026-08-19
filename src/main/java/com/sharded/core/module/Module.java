package com.sharded.core.module;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.Text;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.Prefix;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every ShardedCore module. Each module owns a folder
 * (plugins/ShardedCore/modules/&lt;id&gt;/) containing its config.yml and messages.yml.
 */
public abstract class Module implements Listener {

    protected final ShardedCore plugin;
    private final String id;
    private final List<String> commands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    protected YamlConfiguration config;
    protected YamlConfiguration messages;
    private boolean enabled;

    protected Module(ShardedCore plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public final String id() {
        return id;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public final void enable() {
        loadConfigs();
        onEnable();
        registerListener(this);
        enabled = true;
    }

    public final void disable() {
        if (!enabled) return;
        try {
            onDisable();
        } finally {
            for (Listener l : listeners) HandlerList.unregisterAll(l);
            listeners.clear();
            for (String cmd : commands) {
                PluginCommand pc = plugin.getCommand(cmd);
                if (pc != null) {
                    pc.setExecutor(null);
                    pc.setTabCompleter(null);
                }
            }
            commands.clear();
            enabled = false;
        }
    }

    protected abstract void onEnable();

    protected void onDisable() {
    }

    /* ------------------------------------------------------------ */

    public final void loadConfigs() {
        this.config = loadYaml("config.yml");
        this.messages = loadYaml("messages.yml");
    }

    private YamlConfiguration loadYaml(String fileName) {
        File folder = new File(plugin.getDataFolder(), "modules/" + id);
        if (!folder.exists()) folder.mkdirs();
        File file = new File(folder, fileName);
        String resourcePath = "modules/" + id + "/" + fileName;

        if (!file.exists() && plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
        }

        YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();

        // Merge in new default keys from the jar so updates don't break old files.
        InputStream defaults = plugin.getResource(resourcePath);
        if (defaults != null) {
            YamlConfiguration def = YamlConfiguration.loadConfiguration(new InputStreamReader(defaults, StandardCharsets.UTF_8));
            yaml.setDefaults(def);
            yaml.options().copyDefaults(true);
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save " + resourcePath + ": " + e.getMessage());
            }
        }
        return yaml;
    }

    public final File moduleFolder() {
        File folder = new File(plugin.getDataFolder(), "modules/" + id);
        if (!folder.exists()) folder.mkdirs();
        return folder;
    }

    /* ------------------------------------------------------------ */

    protected final void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    protected final void registerCommand(String name, CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().warning("[" + id + "] Command '" + name + "' is missing from plugin.yml!");
            return;
        }
        cmd.setExecutor(executor);
        if (executor instanceof TabCompleter tab) cmd.setTabCompleter(tab);
        commands.add(name);
    }

    /* ------------------------------------------------------------ */

    /** Prefix for this module's messages — config {@code prefix} overrides messages.yml. */
    protected String messagePrefix() {
        if (config != null && config.isString("prefix")) {
            return ColorUtil.normalize(config.getString("prefix"));
        }
        String modulePrefix = messages.getString("prefix", "%prefix%");
        return ColorUtil.normalize(modulePrefix.replace("%prefix%", Prefix.get()));
    }

    /** Raw message from messages.yml with module prefix and placeholders applied. */
    public final String raw(String key, String... replacements) {
        String msg = messages.getString(key, "<missing message: " + id + "/" + key + ">");
        msg = msg.replace("%prefix%", messagePrefix());
        return Text.apply(msg, replacements);
    }

    /** Sends a message from messages.yml. Empty messages are skipped. */
    public final void send(CommandSender to, String key, String... replacements) {
        String msg = raw(key, replacements);
        if (msg.isEmpty()) return;
        to.sendMessage(Text.c(msg));
    }
}

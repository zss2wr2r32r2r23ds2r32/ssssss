package com.sharded.core.module;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.MessageUtil;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every ShardedCore module. Each module owns a folder
 * (plugins/ShardedCore/modules/&lt;id&gt;/) containing its config.yml and messages.yml.
 */
public abstract class Module implements Listener {

    protected final ShardedCore plugin;
    private final String category;
    private final String id;
    private final List<String> commands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    protected YamlConfiguration config;
    protected YamlConfiguration messages;
    private boolean enabled;

    protected Module(ShardedCore plugin, String id) {
        this.plugin = plugin;
        this.category = ModuleCategories.categoryOf(id);
        this.id = id;
    }

    protected Module(ShardedCore plugin, String category, String id) {
        this.plugin = plugin;
        this.category = category == null ? ModuleCategories.categoryOf(id) : category;
        this.id = id;
    }

    public final String categoryLabel() {
        return category;
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
        migrateLegacyFolder();
        File folder = moduleFolder();
        File file = new File(folder, fileName);
        String resourcePath = resolveResourcePath(fileName);
        return ConfigSync.load(plugin, file, resourcePath);
    }

    /** Jar path for an extra file (gui, menus, etc.) with categorized + legacy fallback. */
    protected final String jarResourcePath(String fileName) {
        String categorized = ModulePaths.resourcePath(id, fileName);
        if (plugin.getResource(categorized) != null) return categorized;
        String legacy = "modules/" + id + "/" + fileName;
        if (plugin.getResource(legacy) != null) return legacy;
        return categorized;
    }

    /** Copies a jar resource into this module folder when missing or outdated. */
    protected final File syncJarResource(String fileName) {
        File file = new File(moduleFolder(), fileName);
        ConfigSync.sync(plugin, file, jarResourcePath(fileName));
        return file;
    }

    private String resolveResourcePath(String fileName) {
        return jarResourcePath(fileName);
    }

    private void migrateLegacyFolder() {
        if ("core".equals(category)) return;
        File legacy = new File(plugin.getDataFolder(), "modules/" + id);
        File target = moduleFolder();
        if (!legacy.exists() || legacy.getAbsolutePath().equals(target.getAbsolutePath())) return;
        if (target.exists() && target.list() != null && target.list().length > 0) return;
        try {
            java.nio.file.Files.walk(legacy.toPath()).forEach(path -> {
                try {
                    java.nio.file.Path dest = target.toPath().resolve(legacy.toPath().relativize(path));
                    if (java.nio.file.Files.isDirectory(path)) {
                        java.nio.file.Files.createDirectories(dest);
                    } else {
                        java.nio.file.Files.createDirectories(dest.getParent());
                        java.nio.file.Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception ignored) {
                }
            });
        } catch (Exception e) {
            plugin.getLogger().warning("Could not migrate module folder for " + id + ": " + e.getMessage());
        }
    }

    public final File moduleFolder() {
        File folder = ModulePaths.moduleFolder(plugin, id);
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
        if (executor instanceof TabCompleter tab) {
            cmd.setTabCompleter(tab);
        } else {
            cmd.setTabCompleter(plugin.coreTabComplete());
        }
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

    /** Lore/message lines from messages.yml (string list or single string). */
    public final List<String> rawList(String key, String... replacements) {
        List<String> lines = new ArrayList<>(messages.getStringList(key));
        if (lines.isEmpty()) {
            String single = messages.getString(key);
            if (single != null && !single.isEmpty()) lines.add(single);
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(Text.apply(line.replace("%prefix%", messagePrefix()), replacements));
        }
        return out;
    }

    /** Sends a message from messages.yml. Empty messages are skipped. */
    public final void send(CommandSender to, String key, String... replacements) {
        String msg = raw(key, replacements);
        if (msg.isEmpty()) return;
        MessageUtil.deliver(to, Text.c(msg), resolveDelivery(key));
    }

    /** chat | actionbar | both — per-key override in messages.yml {@code message-modes}, then module config, then global. */
    public final MessageUtil.Delivery resolveDelivery(String key) {
        if (messages != null && messages.isString("message-modes." + key)) {
            MessageUtil.Delivery mode = MessageUtil.Delivery.parse(messages.getString("message-modes." + key));
            if (mode != null) return mode;
        }
        if (config != null && config.isString("message-mode")) {
            MessageUtil.Delivery mode = MessageUtil.Delivery.parse(config.getString("message-mode"));
            if (mode != null) return mode;
        }
        MessageUtil.Delivery global = MessageUtil.Delivery.parse(plugin.getConfig().getString("message-mode", "chat"));
        return global != null ? global : MessageUtil.Delivery.CHAT;
    }
}

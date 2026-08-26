package com.shardedcore.module;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {

    public static final String DISABLED_PERMISSION = "shardedcore.internal.disabled";

    protected final ShardedCore plugin;
    protected final String id;
    protected File folder;
    protected FileConfiguration config;
    private final List<String> commands = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    protected Module(ShardedCore plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    public String id() {
        return id;
    }

    public File folder() {
        return folder;
    }

    public FileConfiguration config() {
        return config;
    }

    public void loadFiles() {
        folder = new File(plugin.getDataFolder(), "modules/" + id);
        if (!folder.exists() && !folder.mkdirs()) {
            plugin.getLogger().warning("Could not create module folder " + id);
        }
        File configFile = new File(folder, "config.yml");
        Configs.saveDefault(plugin, "modules/" + id + "/config.yml", configFile);
        extraFiles();
        config = Configs.load(configFile);
    }

    protected void extraFiles() {
    }

    public boolean enabledInConfig() {
        return plugin.moduleEnabled(id);
    }

    public abstract void enable();

    public abstract void disable();

    public void reload() {
        loadFiles();
    }

    protected void extraFile(String name) {
        Configs.saveDefault(plugin, "modules/" + id + "/" + name, new File(folder, name));
    }

    protected void registerCommand(String name, CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            plugin.getLogger().warning("[" + id + "] missing command '" + name + "' in plugin.yml");
            return;
        }
        plugin.restoreCommandPermission(command);
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
        commands.add(name);
    }

    protected void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    public List<String> boundCommands() {
        return List.copyOf(commands);
    }

    public void markDisabled() {
        hideBoundCommands();
        cleanupListeners();
    }

    protected void cleanup() {
        hideBoundCommands();
        cleanupListeners();
    }

    private void hideBoundCommands() {
        for (String name : commands) {
            PluginCommand command = plugin.getCommand(name);
            if (command == null) continue;
            plugin.hideCommand(command);
        }
        commands.clear();
    }

    private void cleanupListeners() {
        for (Listener listener : listeners) HandlerList.unregisterAll(listener);
        listeners.clear();
    }

    protected String cfg(String path, String fallback) {
        return config.getString(path, fallback);
    }

    protected String message(String path, String... pairs) {
        return Text.apply(cfg("messages." + path, cfg(path, "")), pairs);
    }

    protected void send(CommandSender to, String path, String... pairs) {
        String text = message(path, pairs);
        if (text == null || text.isEmpty()) return;
        boolean bar = config.getBoolean("messages.actionbar", false)
                || config.getBoolean("actionbar", false)
                || config.getBoolean("actionbar." + path, false)
                || config.getBoolean("messages." + path + "-actionbar", false);
        if (to instanceof Player player && bar && !path.startsWith("usage")) {
            player.sendActionBar(ColorUtil.parse(text));
            return;
        }
        to.sendMessage(ColorUtil.parse(text));
    }

    protected void sendBar(CommandSender to, String path, String... pairs) {
        String text = message(path, pairs);
        if (text == null || text.isEmpty()) return;
        if (to instanceof Player player) {
            player.sendActionBar(ColorUtil.parse(text));
            return;
        }
        to.sendMessage(ColorUtil.parse(text));
    }

    protected void sendRaw(CommandSender to, String message) {
        if (message == null || message.isEmpty()) return;
        to.sendMessage(ColorUtil.parse(message));
    }

    protected void sendRawBar(CommandSender to, String message) {
        if (message == null || message.isEmpty()) return;
        if (to instanceof Player player) {
            player.sendActionBar(ColorUtil.parse(message));
            return;
        }
        to.sendMessage(ColorUtil.parse(message));
    }

    protected void sendLines(CommandSender to, List<String> lines, String url, String... pairs) {
        if (lines == null || lines.isEmpty()) return;
        int last = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i) != null && !lines.get(i).isBlank()) {
                last = i;
                break;
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null) continue;
            Component part = ColorUtil.parse(Text.apply(line, pairs));
            if (i == last && url != null && !url.isBlank()) {
                String href = url.startsWith("http") ? url : "https://" + url;
                part = part.clickEvent(ClickEvent.openUrl(href))
                        .hoverEvent(HoverEvent.showText(ColorUtil.parse("&7Click to open")));
            }
            to.sendMessage(part);
        }
    }

    protected void sound(Player player, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section != null) {
            Sounds.play(player, section);
            return;
        }
        String name = config.getString(path, "");
        Sounds.play(player, name, 1f, 1f);
    }

    public ShardedCore plugin() {
        return plugin;
    }
}

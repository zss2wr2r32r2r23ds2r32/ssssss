package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AnnouncementsModule implements Module, CommandExecutor {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private BukkitTask announcementTask;
    private final Random random = new Random();
    private Map<String, String> variables = new HashMap<>();

    @Override
    public String getId() {
        return "announcements";
    }

    @Override
    public String getDisplayName() {
        return "Announcements";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        reloadVariables();

        if (config.getBoolean("commands.discord.enabled", true) && plugin.getCommand("discord") != null) {
            plugin.getCommand("discord").setExecutor(this);
        }
        if (config.getBoolean("commands.store.enabled", true) && plugin.getCommand("store") != null) {
            plugin.getCommand("store").setExecutor(this);
        }

        if (config.getBoolean("broadcast.enabled", true)) {
            long intervalTicks = config.getLong("broadcast.interval-seconds", 420) * 20L;
            announcementTask = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastRandom, intervalTicks, intervalTicks);
        }
    }

    @Override
    public void disable() {
        if (announcementTask != null) {
            announcementTask.cancel();
            announcementTask = null;
        }
        if (plugin.getCommand("discord") != null) {
            plugin.getCommand("discord").setExecutor(null);
        }
        if (plugin.getCommand("store") != null) {
            plugin.getCommand("store").setExecutor(null);
        }
    }

    private void reloadVariables() {
        variables = new HashMap<>();
        if (config.isConfigurationSection("variables")) {
            for (String key : config.getConfigurationSection("variables").getKeys(false)) {
                variables.put(key, config.getString("variables." + key, ""));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("discord".equalsIgnoreCase(command.getName())) {
            sendMessageLines(sender, config.getStringList("discord.messages"));
            return true;
        }
        if ("store".equalsIgnoreCase(command.getName())) {
            sendMessageLines(sender, config.getStringList("store.messages"));
            return true;
        }
        return false;
    }

    private void broadcastRandom() {
        List<String> types = config.getStringList("broadcast.types");
        if (types.isEmpty()) {
            types = List.of("discord", "store");
        }
        String type = types.get(random.nextInt(types.size()));
        if ("discord".equalsIgnoreCase(type)) {
            broadcastLines(config.getStringList("discord.messages"));
        } else {
            broadcastLines(config.getStringList("store.messages"));
        }
    }

    public void sendMessageLines(CommandSender sender, List<String> lines) {
        Player player = sender instanceof Player p ? p : null;
        MessageUtil.sendRichLines(sender, lines, player, variables);
    }

    private void broadcastLines(List<String> lines) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            MessageUtil.sendRichLines(player, lines, player, variables);
        }
    }
}

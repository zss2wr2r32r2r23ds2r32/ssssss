package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatPrefixModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

    @Override
    public String getId() {
        return "chat-prefixes";
    }

    @Override
    public String getDisplayName() {
        return "Chat Prefixes";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!config.getBoolean("enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        String prefix = getPrefix(player);
        String format = config.getString("format", "%prefix%%player%&8: &f%message%")
                .replace("%prefix%", prefix)
                .replace("%player%", player.getName())
                .replace("%message%", event.getMessage());

        event.setFormat(MessageUtil.colorize(format));
    }

    private String getPrefix(Player player) {
        ConfigurationSection prefixes = config.getConfigurationSection("prefixes");
        if (prefixes == null) {
            return MessageUtil.colorize(config.getString("default-prefix", "&7"));
        }

        String bestPrefix = config.getString("default-prefix", "&7");
        int bestWeight = -1;

        for (String key : prefixes.getKeys(false)) {
            ConfigurationSection section = prefixes.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            String permission = section.getString("permission");
            if (permission != null && player.hasPermission(permission)) {
                int weight = section.getInt("weight", 0);
                if (weight > bestWeight) {
                    bestWeight = weight;
                    bestPrefix = section.getString("prefix", bestPrefix);
                }
            }
        }

        return MessageUtil.colorize(bestPrefix);
    }
}

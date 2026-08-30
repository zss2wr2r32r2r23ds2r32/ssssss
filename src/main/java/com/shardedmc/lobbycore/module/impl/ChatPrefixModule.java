package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
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
        return "Chat Format";
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
        String prefix = resolvePlaceholder(player,
                config.getString("prefix-placeholder", "%luckperms_prefix%"),
                config.getString("default-prefix", "&7"));
        String suffix = resolvePlaceholder(player,
                config.getString("suffix-placeholder", "%luckperms_suffix%"),
                config.getString("default-suffix", ""));
        String tag = resolvePlaceholder(player,
                config.getString("tag-placeholder", "%shardedcore_tag%"),
                config.getString("default-tag", ""));
        String name = resolvePlaceholder(player,
                config.getString("name-placeholder", "%player_name%"),
                player.getName());

        String format = config.getString("format", "{prefix}{name}{tag} &8▷ &r{message}")
                .replace("{prefix}", prefix)
                .replace("{suffix}", suffix)
                .replace("{tag}", tag)
                .replace("{name}", name)
                .replace("{message}", "%2$s")
                .replace("%prefix%", prefix)
                .replace("%suffix%", suffix)
                .replace("%tag%", tag)
                .replace("%player%", name)
                .replace("%message%", "%2$s");

        if (!format.contains("%2$s")) {
            format = format + "%2$s";
        }

        String colored = MessageUtil.colorize(format);
        // Escape % for String.format, but keep %2$s
        colored = colored.replace("%", "%%").replace("%%2$s", "%2$s").replace("%%1$s", "%1$s");
        event.setFormat(colored);
    }

    private String resolvePlaceholder(Player player, String placeholder, String fallback) {
        if (placeholder == null || placeholder.isEmpty()) {
            return fallback == null ? "" : fallback;
        }
        String resolved = MessageUtil.applyPapi(player, placeholder);
        if (resolved == null || resolved.equals(placeholder)) {
            return fallback == null ? "" : fallback;
        }
        return resolved;
    }
}

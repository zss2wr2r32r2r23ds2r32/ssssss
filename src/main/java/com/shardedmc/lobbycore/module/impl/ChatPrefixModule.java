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
        String prefix = resolvePrefix(player);
        String suffix = resolveSuffix(player);
        String tag = resolveTag(player);
        String name = resolveName(player);

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
        colored = colored.replace("%", "%%").replace("%%2$s", "%2$s").replace("%%1$s", "%1$s");
        event.setFormat(colored);
    }

    private String resolvePrefix(Player player) {
        String fromPapi = resolvePlaceholder(player,
                config.getString("prefix-placeholder", "%luckperms_prefix%"), null);
        if (fromPapi != null && !fromPapi.isBlank()) {
            return ensureTrailingSpace(fromPapi);
        }
        String fromLp = luckPermsMeta(player, "prefix");
        if (fromLp != null && !fromLp.isBlank()) {
            return ensureTrailingSpace(fromLp);
        }
        String fallback = config.getString("default-prefix", "");
        return fallback == null ? "" : fallback;
    }

    private String resolveSuffix(Player player) {
        String fromPapi = resolvePlaceholder(player,
                config.getString("suffix-placeholder", "%luckperms_suffix%"), null);
        if (fromPapi != null && !fromPapi.isBlank()) {
            return fromPapi;
        }
        String fromLp = luckPermsMeta(player, "suffix");
        if (fromLp != null && !fromLp.isBlank()) {
            return fromLp;
        }
        String fallback = config.getString("default-suffix", "");
        return fallback == null ? "" : fallback;
    }

    private String resolveTag(Player player) {
        String fromPapi = resolvePlaceholder(player,
                config.getString("tag-placeholder", "%shardedcore_tag%"), null);
        if (fromPapi != null && !fromPapi.isBlank()) {
            return fromPapi;
        }
        String fallback = config.getString("default-tag", "");
        return fallback == null ? "" : fallback;
    }

    private String resolveName(Player player) {
        String fromPapi = resolvePlaceholder(player,
                config.getString("name-placeholder", "%player_name%"), null);
        if (fromPapi != null && !fromPapi.isBlank()) {
            return fromPapi;
        }
        return player.getName();
    }

    private String ensureTrailingSpace(String value) {
        if (value.isEmpty() || value.endsWith(" ")) {
            return value;
        }
        // Rank prefixes usually need a space before the name
        return value + " ";
    }

    private String resolvePlaceholder(Player player, String placeholder, String fallback) {
        if (placeholder == null || placeholder.isEmpty()) {
            return fallback;
        }
        String resolved = MessageUtil.applyPapi(player, placeholder);
        if (resolved == null || resolved.equals(placeholder)) {
            return fallback;
        }
        return resolved;
    }

    private String luckPermsMeta(Player player, String type) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            return null;
        }
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object adapter = api.getClass().getMethod("getPlayerAdapter", Class.class).invoke(api, Player.class);
            Object user = adapter.getClass().getMethod("getUser", Player.class).invoke(adapter, player);
            Object cached = user.getClass().getMethod("getCachedData").invoke(user);
            Object meta = cached.getClass().getMethod("getMetaData").invoke(cached);
            if ("prefix".equals(type)) {
                return (String) meta.getClass().getMethod("getPrefix").invoke(meta);
            }
            if ("suffix".equals(type)) {
                return (String) meta.getClass().getMethod("getSuffix").invoke(meta);
            }
        } catch (ReflectiveOperationException ex) {
            plugin.getLogger().warning("LuckPerms chat meta lookup failed: " + ex.getMessage());
        }
        return null;
    }
}

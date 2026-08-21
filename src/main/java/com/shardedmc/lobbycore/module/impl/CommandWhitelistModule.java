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
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandWhitelistModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private List<Pattern> whitelistPatterns;

    @Override
    public String getId() {
        return "command-whitelist";
    }

    @Override
    public String getDisplayName() {
        return "Command Whitelist";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        compilePatterns();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    private void compilePatterns() {
        whitelistPatterns = config.getStringList("whitelist").stream()
                .map(pattern -> Pattern.compile("^" + pattern.toLowerCase(Locale.ROOT) + ".*"))
                .collect(Collectors.toList());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("shardedlobbycore.bypass.commandwhitelist")) {
            return;
        }

        String command = event.getMessage().substring(1).toLowerCase(Locale.ROOT).split(" ")[0];

        for (Pattern pattern : whitelistPatterns) {
            if (pattern.matcher(command).matches()) {
                return;
            }
        }

        event.setCancelled(true);
        MessageUtil.sendFormatted(player, config.getString("message", "&cYou cannot use that command in the lobby."));
    }
}

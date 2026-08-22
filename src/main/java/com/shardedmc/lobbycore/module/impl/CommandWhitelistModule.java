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
import org.bukkit.event.server.TabCompleteEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandWhitelistModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private List<Pattern> whitelistPatterns;
    private List<String> whitelistCommands;

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
        whitelistCommands = config.getStringList("whitelist").stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        whitelistPatterns = whitelistCommands.stream()
                .map(pattern -> Pattern.compile("^" + pattern + "($|:).*"))
                .collect(Collectors.toList());
    }

    private boolean isWhitelisted(String command) {
        String base = command.toLowerCase(Locale.ROOT).split(" ")[0];
        for (Pattern pattern : whitelistPatterns) {
            if (pattern.matcher(base).matches()) {
                return true;
            }
        }
        return false;
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

        String command = event.getMessage().substring(1);
        if (isWhitelisted(command)) {
            return;
        }

        event.setCancelled(true);
        MessageUtil.sendFormatted(player, config.getString("message", "%prefix% &#FF2727You cannot use that command in the lobby."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!config.getBoolean("enabled", true) || !config.getBoolean("filter-tab-complete", true)) {
            return;
        }
        if (!(event.getSender() instanceof Player player)) {
            return;
        }
        if (player.hasPermission("shardedlobbycore.bypass.commandwhitelist")) {
            return;
        }

        String buffer = event.getBuffer();
        if (!buffer.startsWith("/")) {
            return;
        }

        String typed = buffer.substring(1).toLowerCase(Locale.ROOT);
        boolean hasSpace = typed.contains(" ");
        String base = hasSpace ? typed.split(" ")[0] : typed;

        if (!hasSpace) {
            List<String> filtered = new ArrayList<>();
            for (String allowed : whitelistCommands) {
                if (allowed.startsWith(base)) {
                    filtered.add(allowed);
                }
            }
            event.getCompletions().clear();
            event.getCompletions().addAll(filtered);
            return;
        }

        if (!isWhitelisted(typed)) {
            event.getCompletions().clear();
        } else {
            Iterator<String> iterator = event.getCompletions().iterator();
            while (iterator.hasNext()) {
                String completion = iterator.next();
                if (completion.startsWith("/")) {
                    completion = completion.substring(1);
                }
                if (!isWhitelisted(base + " " + completion.split(" ")[0])) {
                    iterator.remove();
                }
            }
        }
    }
}

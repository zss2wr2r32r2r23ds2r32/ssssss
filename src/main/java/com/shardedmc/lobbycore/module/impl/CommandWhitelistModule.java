package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.EventExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class CommandWhitelistModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private Set<String> whitelistCommands;

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
        compileWhitelist();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerPaperCommandSendListener();
    }

    private void registerPaperCommandSendListener() {
        if (!config.getBoolean("filter-command-send", true)) {
            return;
        }
        try {
            Class<? extends Event> eventClass = Class.forName("com.destroystokyo.paper.event.brigadier.PlayerCommandSendEvent")
                    .asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> {
                try {
                    Player player = (Player) eventClass.getMethod("getPlayer").invoke(event);
                    if (canBypass(player)) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    Collection<String> commands = (Collection<String>) eventClass.getMethod("getCommands").invoke(event);
                    commands.removeIf(cmd -> !isWhitelisted(cmd));
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Error filtering command send: " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST, executor, plugin, false);
        } catch (ReflectiveOperationException ex) {
            try {
                Class<? extends Event> eventClass = Class.forName("io.papermc.paper.event.player.PlayerCommandSendEvent")
                        .asSubclass(Event.class);
                EventExecutor executor = (listener, event) -> {
                    try {
                        Player player = (Player) eventClass.getMethod("getPlayer").invoke(event);
                        if (canBypass(player)) {
                            return;
                        }
                        @SuppressWarnings("unchecked")
                        Collection<String> commands = (Collection<String>) eventClass.getMethod("getCommands").invoke(event);
                        commands.removeIf(cmd -> !isWhitelisted(cmd));
                    } catch (ReflectiveOperationException inner) {
                        plugin.getLogger().warning("Error filtering command send: " + inner.getMessage());
                    }
                };
                Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST, executor, plugin, false);
            } catch (ReflectiveOperationException ignored) {
                plugin.getLogger().info("Paper command-send filtering unavailable on this server version.");
            }
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    private void compileWhitelist() {
        whitelistCommands = config.getStringList("whitelist").stream()
                .map(s -> normalizeBaseCommand(s.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalizeBaseCommand(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        command = command.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        int colon = command.indexOf(':');
        if (colon >= 0) {
            command = command.substring(colon + 1);
        }
        return command.split(" ")[0];
    }

    private String extractBaseCommand(String fullCommand) {
        return normalizeBaseCommand(fullCommand);
    }

    private boolean isWhitelisted(String command) {
        String base = extractBaseCommand(command);
        if (whitelistCommands.contains(base)) {
            return true;
        }
        for (String allowed : whitelistCommands) {
            if (base.equals(allowed) || command.toLowerCase(Locale.ROOT).startsWith(allowed + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean canBypass(Player player) {
        return player.hasPermission("shardedlobbycore.bypass.commandwhitelist");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (canBypass(player)) {
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
        if (canBypass(player)) {
            return;
        }

        String buffer = event.getBuffer();
        if (!buffer.startsWith("/")) {
            return;
        }

        String typed = buffer.substring(1).toLowerCase(Locale.ROOT);
        boolean hasSpace = typed.contains(" ");
        String base = hasSpace ? typed.split(" ")[0] : typed;
        base = normalizeBaseCommand(base);

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

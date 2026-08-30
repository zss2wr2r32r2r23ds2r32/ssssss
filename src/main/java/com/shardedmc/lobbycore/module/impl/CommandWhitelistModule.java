package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Removes non-whitelisted commands from the client's command list so they render red,
 * and blocks execution of anything not on the whitelist.
 */
public class CommandWhitelistModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private Set<String> whitelist;
    private Set<String> blocked;

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
        compileLists();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        registerCommandSendListener();
        registerAsyncTabCompleteListener();
        plugin.getLogger().info("Command whitelist active with " + whitelist.size() + " allowed roots");
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    private void compileLists() {
        whitelist = config.getStringList("whitelist").stream()
                .map(this::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        blocked = config.getStringList("blocked").stream()
                .map(this::normalize)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        // Always strip these by default
        blocked.addAll(List.of("plugins", "pl", "help", "?", "version", "ver", "about", "icanhasbukkit"));
    }

    private String normalize(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        String value = command.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        return value.split(" ")[0];
    }

    private boolean canBypass(Player player) {
        if (player.hasPermission("shardedlobbycore.bypass.commandwhitelist")) {
            return true;
        }
        return config.getBoolean("bypass-for-op", false) && player.isOp();
    }

    private boolean isAllowed(String command) {
        String root = normalize(command);
        if (root.isEmpty()) {
            return false;
        }
        if (blocked.contains(root)) {
            return false;
        }
        return whitelist.contains(root);
    }

    private void registerCommandSendListener() {
        if (!config.getBoolean("filter-command-send", true)) {
            return;
        }
        String[] classes = {
                "com.destroystokyo.paper.event.brigadier.PlayerCommandSendEvent",
                "io.papermc.paper.event.player.PlayerCommandSendEvent"
        };
        for (String className : classes) {
            if (tryRegister(className, (listener, event) -> {
                try {
                    Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    if (canBypass(player)) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    Collection<String> commands = (Collection<String>) event.getClass()
                            .getMethod("getCommands").invoke(event);
                    List<String> allowed = commands.stream()
                            .filter(this::isAllowed)
                            .collect(Collectors.toList());
                    commands.clear();
                    commands.addAll(allowed);
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Command whitelist send filter error: " + ex.getMessage());
                }
            })) {
                plugin.getLogger().info("Hooked PlayerCommandSendEvent for red command filtering");
                return;
            }
        }
        plugin.getLogger().warning("Could not hook PlayerCommandSendEvent — commands may not appear red");
    }

    private void registerAsyncTabCompleteListener() {
        if (!config.getBoolean("filter-tab-complete", true)) {
            return;
        }
        tryRegister("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent", (listener, event) -> {
            try {
                Object sender = event.getClass().getMethod("getSender").invoke(event);
                if (!(sender instanceof Player player) || canBypass(player)) {
                    return;
                }
                boolean isCommand = (boolean) event.getClass().getMethod("isCommand").invoke(event);
                if (!isCommand) {
                    return;
                }
                String buffer = (String) event.getClass().getMethod("getBuffer").invoke(event);
                if (buffer == null || !buffer.startsWith("/")) {
                    return;
                }
                @SuppressWarnings("unchecked")
                List<String> completions = (List<String>) event.getClass().getMethod("getCompletions").invoke(event);
                filterCompletions(buffer, completions);
            } catch (ReflectiveOperationException ex) {
                plugin.getLogger().warning("Async tab complete filter error: " + ex.getMessage());
            }
        });
    }

    private boolean tryRegister(String className, EventExecutor executor) {
        try {
            Class<? extends Event> eventClass = Class.forName(className).asSubclass(Event.class);
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST, executor, plugin, false);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private void filterCompletions(String buffer, List<String> completions) {
        String typed = buffer.substring(1).toLowerCase(Locale.ROOT);
        boolean hasSpace = typed.contains(" ");
        String root = normalize(hasSpace ? typed.split(" ")[0] : typed);

        if (hasSpace) {
            if (!isAllowed(root)) {
                completions.clear();
            }
            return;
        }

        completions.clear();
        for (String allowed : whitelist) {
            if (root.isEmpty() || allowed.startsWith(root)) {
                if (!blocked.contains(allowed)) {
                    completions.add(allowed);
                }
            }
        }
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
        if (isAllowed(command)) {
            return;
        }
        event.setCancelled(true);
        MessageUtil.sendFormatted(player, config.getString("message",
                "&#FF0000&lERROR &8▷ &fYou cannot use that command here."));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (!config.getBoolean("enabled", true) || !config.getBoolean("filter-tab-complete", true)) {
            return;
        }
        if (!(event.getSender() instanceof Player player) || canBypass(player)) {
            return;
        }
        String buffer = event.getBuffer();
        if (!buffer.startsWith("/")) {
            return;
        }
        filterCompletions(buffer, event.getCompletions());
    }
}

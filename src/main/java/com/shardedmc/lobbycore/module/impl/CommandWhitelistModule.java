package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.EventExecutor;

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
        registerAsyncBrigadierFilter();
        // Refresh already-online players so filter applies immediately after reload
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!canBypass(player)) {
                    player.updateCommands();
                }
            }
        }, 20L);
        plugin.getLogger().info("Command whitelist active with " + whitelist.size() + " allowed roots");
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    private void compileLists() {
        whitelist = config.getStringList("whitelist").stream()
                .map(this::normalizeRoot)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        blocked = config.getStringList("blocked").stream()
                .map(this::normalizeRoot)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        blocked.addAll(List.of(
                "plugins", "pl", "help", "?", "version", "ver", "about",
                "icanhasbukkit", "paper", "spigot", "bukkit", "minecraft"
        ));
    }

    private String normalizeRoot(String command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        String value = command.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value.split(" ")[0];
    }

    private boolean canBypass(Player player) {
        if (player.hasPermission("shardedlobbycore.bypass.commandwhitelist")) {
            return true;
        }
        return config.getBoolean("bypass-for-op", false) && player.isOp();
    }

    /**
     * Command labels from PlayerCommandSendEvent can be "msg" or "essentials:msg".
     * Only exact whitelist roots are allowed — namespaced aliases must also be listed
     * or match the un-namespaced root if that root is whitelisted.
     */
    private boolean isAllowedLabel(String label) {
        if (label == null || label.isEmpty()) {
            return false;
        }
        String root = normalizeRoot(label);
        String bare = root.contains(":") ? root.substring(root.indexOf(':') + 1) : root;

        if (blocked.contains(root) || blocked.contains(bare)) {
            return false;
        }
        return whitelist.contains(root) || whitelist.contains(bare);
    }

    private void registerAsyncBrigadierFilter() {
        String[] classes = {
                "com.destroystokyo.paper.event.brigadier.AsyncPlayerSendCommandsEvent",
                "io.papermc.paper.event.brigadier.AsyncPlayerSendCommandsEvent"
        };
        for (String className : classes) {
            if (tryRegister(className, (listener, event) -> {
                try {
                    Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    if (canBypass(player)) {
                        return;
                    }
                    // Only mutate once (event can fire sync + async)
                    try {
                        boolean hasFiredAsync = (boolean) event.getClass().getMethod("hasFiredAsync").invoke(event);
                        boolean async = event.getClass().getMethod("isAsynchronous").invoke(event) instanceof Boolean b && b;
                        if (!async && hasFiredAsync) {
                            return;
                        }
                    } catch (NoSuchMethodException ignored) {
                    }

                    Object root = event.getClass().getMethod("getCommandNode").invoke(event);
                    @SuppressWarnings("unchecked")
                    Map<String, ?> children = (Map<String, ?>) root.getClass().getMethod("getChildren").invoke(root);
                    // getChildren often returns unmodifiable view — collect names then remove
                    List<String> toRemove = new ArrayList<>();
                    for (Object child : new ArrayList<>(children.values())) {
                        String name = (String) child.getClass().getMethod("getName").invoke(child);
                        if (!isAllowedLabel(name)) {
                            toRemove.add(name);
                        }
                    }
                    for (String name : toRemove) {
                        root.getClass().getMethod("removeChild", String.class).invoke(root, name);
                    }
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Brigadier command filter error: " + ex.getMessage());
                }
            })) {
                plugin.getLogger().info("Hooked AsyncPlayerSendCommandsEvent for command filtering");
                return;
            }
        }
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!config.getBoolean("enabled", true) || !config.getBoolean("filter-command-send", true)) {
            return;
        }
        if (canBypass(event.getPlayer())) {
            return;
        }
        // Paper docs: only remove entries — do not clear/addAll
        event.getCommands().removeIf(label -> !isAllowedLabel(label));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !canBypass(player)) {
                player.updateCommands();
            }
        }, 40L);
    }

    private void filterCompletions(String buffer, List<String> completions) {
        String typed = buffer.substring(1).toLowerCase(Locale.ROOT);
        boolean hasSpace = typed.contains(" ");
        String root = normalizeRoot(hasSpace ? typed.split(" ")[0] : typed);
        String bare = root.contains(":") ? root.substring(root.indexOf(':') + 1) : root;

        if (hasSpace) {
            if (!isAllowedLabel(root)) {
                completions.clear();
            }
            return;
        }

        completions.removeIf(completion -> {
            String label = normalizeRoot(completion);
            return !isAllowedLabel(label) || !(bare.isEmpty() || label.startsWith(bare) ||
                    (label.contains(":") && label.substring(label.indexOf(':') + 1).startsWith(bare)));
        });

        // If vanilla still left junk, replace with whitelist-only matches
        if (completions.isEmpty() || completions.stream().anyMatch(c -> !isAllowedLabel(c))) {
            List<String> allowed = new ArrayList<>();
            for (String entry : whitelist) {
                if (blocked.contains(entry)) {
                    continue;
                }
                if (bare.isEmpty() || entry.startsWith(bare)) {
                    allowed.add(entry);
                }
            }
            completions.clear();
            completions.addAll(allowed);
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
        if (isAllowedLabel(command)) {
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

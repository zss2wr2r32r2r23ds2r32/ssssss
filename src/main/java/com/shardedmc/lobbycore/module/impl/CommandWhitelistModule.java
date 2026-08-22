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
        registerAsyncTabCompleteListener();
        registerBrigadierSuggestionsListener();
    }

    private void registerPaperCommandSendListener() {
        if (!config.getBoolean("filter-command-send", true)) {
            return;
        }
        registerEventListener(
                "com.destroystokyo.paper.event.brigadier.PlayerCommandSendEvent",
                "io.papermc.paper.event.player.PlayerCommandSendEvent",
                event -> {
                    Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    if (canBypass(player)) {
                        return;
                    }
                    @SuppressWarnings("unchecked")
                    Collection<String> commands = (Collection<String>) event.getClass().getMethod("getCommands").invoke(event);
                    List<String> allowed = commands.stream()
                            .filter(this::isWhitelistedRoot)
                            .collect(Collectors.toList());
                    commands.clear();
                    commands.addAll(allowed);
                },
                "PlayerCommandSendEvent"
        );
    }

    private void registerAsyncTabCompleteListener() {
        if (!config.getBoolean("filter-tab-complete", true)) {
            return;
        }
        registerEventListener(
                "com.destroystokyo.paper.event.server.AsyncTabCompleteEvent",
                null,
                event -> {
                    if (!(event.getClass().getMethod("getSender").invoke(event) instanceof Player player)) {
                        return;
                    }
                    if (canBypass(player)) {
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
                    applyTabFilter(buffer, (List<String>) event.getClass().getMethod("getCompletions").invoke(event));
                },
                "AsyncTabCompleteEvent"
        );
    }

    private void registerBrigadierSuggestionsListener() {
        if (!config.getBoolean("filter-brigadier-suggestions", true)) {
            return;
        }
        registerEventListener(
                "com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent",
                null,
                event -> {
                    Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                    if (canBypass(player)) {
                        return;
                    }
                    Object suggestions = event.getClass().getMethod("getSuggestions").invoke(event);
                    Class<?> suggestionsClass = suggestions.getClass();
                    @SuppressWarnings("unchecked")
                    List<Object> original = (List<Object>) suggestionsClass.getMethod("getList").invoke(suggestions);
                    Object range = suggestionsClass.getMethod("getRange").invoke(suggestions);

                    List<Object> filtered = new ArrayList<>();
                    if (shouldFilterBrigadierSuggestions(event, original)) {
                        for (Object suggestion : original) {
                            String text = (String) suggestion.getClass().getMethod("getText").invoke(suggestion);
                            if (isWhitelistedRoot(text)) {
                                filtered.add(suggestion);
                            }
                        }
                    } else {
                        filtered.addAll(original);
                    }

                    Object newSuggestions = suggestionsClass.getMethod("create", range.getClass(), List.class)
                            .invoke(null, range, filtered);
                    event.getClass().getMethod("setSuggestions", suggestionsClass).invoke(event, newSuggestions);
                },
                "AsyncPlayerSendSuggestionsEvent"
        );
    }

    private void registerEventListener(String primaryClass, String fallbackClass, EventHandlerLogic logic, String label) {
        if (tryRegisterEvent(primaryClass, logic)) {
            plugin.getLogger().info("Hooked command whitelist into " + label);
            return;
        }
        if (fallbackClass != null && tryRegisterEvent(fallbackClass, logic)) {
            plugin.getLogger().info("Hooked command whitelist into " + label + " (fallback)");
        }
    }

    private boolean tryRegisterEvent(String className, EventHandlerLogic logic) {
        try {
            Class<? extends Event> eventClass = Class.forName(className).asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> {
                try {
                    logic.handle(event);
                } catch (ReflectiveOperationException ex) {
                    plugin.getLogger().warning("Error in command whitelist (" + className + "): " + ex.getMessage());
                }
            };
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.HIGHEST, executor, plugin, false);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    @FunctionalInterface
    private interface EventHandlerLogic {
        void handle(Object event) throws ReflectiveOperationException;
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

    private boolean isWhitelistedRoot(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT).trim();
        if (lower.startsWith("/")) {
            lower = lower.substring(1);
        }

        if (lower.contains(":")) {
            String label = lower.substring(lower.indexOf(':') + 1).split(" ")[0];
            return whitelistCommands.contains(label);
        }

        return whitelistCommands.contains(lower.split(" ")[0]);
    }

    private boolean isWhitelisted(String command) {
        return isWhitelistedRoot(command);
    }

    private boolean canBypass(Player player) {
        if (player.hasPermission("shardedlobbycore.bypass.commandwhitelist")) {
            return true;
        }
        return config.getBoolean("bypass-for-op", true) && player.isOp();
    }

    private void applyTabFilter(String buffer, List<String> completions) {
        String typed = buffer.substring(1).toLowerCase(Locale.ROOT);
        boolean hasSpace = typed.contains(" ");
        final String commandBase = normalizeBaseCommand(hasSpace ? typed.split(" ")[0] : typed);

        if (hasSpace) {
            if (!whitelistCommands.contains(commandBase)) {
                completions.clear();
            }
            return;
        }

        completions.clear();
        for (String allowed : whitelistCommands) {
            if (commandBase.isEmpty() || allowed.startsWith(commandBase)) {
                completions.add(allowed);
            }
        }
    }

    private boolean shouldFilterBrigadierSuggestions(Object event, List<Object> suggestions) throws ReflectiveOperationException {
        for (String methodName : new String[]{"getBuffer", "getInput", "getMessage"}) {
            try {
                String buffer = (String) event.getClass().getMethod(methodName).invoke(event);
                if (buffer != null && buffer.startsWith("/")) {
                    String typed = buffer.substring(1).toLowerCase(Locale.ROOT);
                    String commandBase = normalizeBaseCommand(typed.split(" ")[0]);
                    if (whitelistCommands.contains(commandBase)) {
                        return false;
                    }
                    if (typed.contains(" ")) {
                        return true;
                    }
                }
            } catch (NoSuchMethodException ignored) {
            }
        }

        if (suggestions.isEmpty()) {
            return false;
        }

        String first = (String) suggestions.get(0).getClass().getMethod("getText").invoke(suggestions.get(0));
        if (first == null || first.isEmpty()) {
            return false;
        }
        if (first.contains(" ") || first.contains(":")) {
            return false;
        }
        return !isWhitelistedRoot(first);
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

        applyTabFilter(buffer, event.getCompletions());
    }
}

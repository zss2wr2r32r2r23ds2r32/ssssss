package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Soft hook into Realized Duels. Restores /duel and /queue when ShardedCore would otherwise
 * claim those commands, and exposes helpers for RTP menu integration.
 */
public final class DuelsHook {

    private final ShardedCore plugin;
    private boolean available;
    private Plugin duelsPlugin;

    public DuelsHook(ShardedCore plugin) {
        this.plugin = plugin;
        try {
            Plugin found = Bukkit.getPluginManager().getPlugin("Duels");
            if (found != null && found.isEnabled()) {
                Class.forName("me.realized.duels.DuelsPlugin");
                duelsPlugin = found;
                available = true;
                plugin.getLogger().info("Hooked into Realized Duels.");
            }
        } catch (Throwable ignored) {
            available = false;
        }
        if (!available) {
            plugin.getLogger().info("Realized Duels not found — install Duels for /duel and /queue.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /** Re-applies Duels command executors (safe after reload or load-order conflicts). */
    public void refreshCommands() {
        if (!available) return;
        try {
            Field commandsField = duelsPlugin.getClass().getDeclaredField("commands");
            commandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> commands = (Map<String, Object>) commandsField.get(duelsPlugin);
            for (String name : List.of("duel", "queue", "duels", "spectate")) {
                Object command = commands.get(name);
                if (command == null) continue;
                Method register = command.getClass().getMethod("register");
                register.invoke(command);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to refresh Realized Duels commands: " + t.getMessage());
        }
    }

    public void openQueue(Player player) {
        if (!available) {
            player.sendMessage("Duels is not available on this server.");
            return;
        }
        dispatch("queue", player, "queue", new String[0]);
    }

    private void dispatch(String commandName, CommandSender sender, String label, String[] args) {
        if (!available) return;
        try {
            Field commandsField = duelsPlugin.getClass().getDeclaredField("commands");
            commandsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> commands = (Map<String, Object>) commandsField.get(duelsPlugin);
            Object command = commands.get(commandName.toLowerCase());
            if (command == null) return;
            invokeRegisteredExecutor(command, sender, label, args);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to dispatch Duels command '" + commandName + "': " + t.getMessage());
        }
    }

    private void invokeRegisteredExecutor(Object abstractCommand, CommandSender sender, String label, String[] args)
            throws ReflectiveOperationException {
        Method playerOnly = abstractCommand.getClass().getMethod("isPlayerOnly");
        Method permission = abstractCommand.getClass().getMethod("getPermission");
        if (Boolean.TRUE.equals(playerOnly.invoke(abstractCommand)) && !(sender instanceof Player)) {
            sender.sendMessage("This command can only be executed by a player.");
            return;
        }
        String perm = (String) permission.invoke(abstractCommand);
        if (perm != null && !sender.hasPermission(perm)) {
            sender.sendMessage("You need the following permission: " + perm);
            return;
        }
        Method executeFirst = findDeclaredMethod(abstractCommand.getClass(), "executeFirst",
                CommandSender.class, String.class, String[].class);
        executeFirst.setAccessible(true);
        if (Boolean.TRUE.equals(executeFirst.invoke(abstractCommand, sender, label, args))) {
            return;
        }
        Method execute = findDeclaredMethod(abstractCommand.getClass(), "execute",
                CommandSender.class, String.class, String[].class);
        execute.setAccessible(true);
        execute.invoke(abstractCommand, sender, label, args);
    }

    private static Method findDeclaredMethod(Class<?> type, String name, Class<?>... params) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }
}

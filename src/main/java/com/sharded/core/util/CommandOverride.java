package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/** Steals command executors from other plugins after the server finishes loading. */
public final class CommandOverride {

    private CommandOverride() {
    }

    public static void takeOver(JavaPlugin plugin, String command, CommandExecutor executor, TabCompleter tab) {
        Runnable apply = () -> {
            PluginCommand cmd = plugin.getCommand(command);
            if (cmd == null) return;
            cmd.setExecutor(executor);
            cmd.setTabCompleter(tab);
        };
        apply.run();
        // Run again after other plugins register their commands.
        Bukkit.getScheduler().runTaskLater(plugin, apply, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, apply, 20L);
        Bukkit.getScheduler().runTaskLater(plugin, apply, 100L);
    }
}

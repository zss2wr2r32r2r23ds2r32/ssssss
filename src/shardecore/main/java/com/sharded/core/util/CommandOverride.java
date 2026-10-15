package com.sharded.core.util;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandOverride {
   private CommandOverride() {
   }

   public static void takeOver(JavaPlugin plugin, String command, CommandExecutor executor, TabCompleter tab) {
      Runnable runnable = () -> {
         PluginCommand plugincommand = plugin.getCommand(command);
         if (plugincommand != null) {
            plugincommand.setExecutor(executor);
            plugincommand.setTabCompleter(tab);
         }
      };
      runnable.run();
      Bukkit.getScheduler().runTaskLater(plugin, runnable, 1L);
      Bukkit.getScheduler().runTaskLater(plugin, runnable, 20L);
      Bukkit.getScheduler().runTaskLater(plugin, runnable, 100L);
   }
}

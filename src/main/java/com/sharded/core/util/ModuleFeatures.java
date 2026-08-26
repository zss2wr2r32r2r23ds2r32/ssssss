package com.sharded.core.util;

import com.sharded.core.module.Module;
import com.sharded.core.module.ModuleManager;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Lists modules and PlaceholderAPI identifiers for /shardedcore features. */
public final class ModuleFeatures {

    private ModuleFeatures() {
    }

    public static void sendFeatures(CommandSender sender, String headerPrefix, ModuleManager modules) {
        sender.sendMessage(Text.c(headerPrefix + "&bShardedCore Modules &7(" + modules.enabledCount() + " enabled)"));
        List<Module> sorted = new ArrayList<>(modules.allModules());
        sorted.sort(Comparator.comparing(Module::id));
        for (Module module : sorted) {
            boolean enabled = modules.isConfiguredEnabled(module.id());
            String color = enabled ? "&a" : "&c";
            String status = enabled ? "ON" : "OFF";
            sender.sendMessage(Text.c("&7- " + color + module.id() + " &8[" + status + "&8] &7(" + module.categoryLabel() + ")"));
        }
    }

    public static void sendPlaceholders(CommandSender sender, String headerPrefix) {
        sender.sendMessage(Text.c(headerPrefix + "&bPlaceholders &7(PlaceholderAPI)"));
        String[] placeholders = {
                "%shardedcore_balance%",
                "%shardedcore_balance_formatted%",
                "%shardedcore_economy_balance%",
                "%shardedcore_economy_balance_formatted%",
                "%shardedcore_tokens%",
                "%shardedcore_tokens_formatted%",
                "%shardedcore_team%",
                "%shardedcore_killstreak%",
                "%shardedcore_killstreak_best%",
                "%token_amount%",
                "%playerpoints_points%",
                "%koth_time%",
                "%outpost_time%"
        };
        for (String ph : placeholders) {
            sender.sendMessage(Text.c("&7- &f" + ph));
        }
    }

    public static void logStartup(org.bukkit.plugin.Plugin plugin, ModuleManager modules) {
        plugin.getLogger().info("=== ShardedCore " + plugin.getDescription().getVersion() + " ===");
        plugin.getLogger().info("Enabled modules: " + modules.enabledCount() + "/" + modules.allModules().size());
        plugin.getLogger().info("Admin: /shardedcore reload | features | placeholders | help");
        plugin.getLogger().info("Economy placeholders: %shardedcore_balance%, %shardedcore_balance_formatted%");
        plugin.getLogger().info("Disable modules in config.yml — disabled commands won't register (vanilla unknown command).");
    }
}

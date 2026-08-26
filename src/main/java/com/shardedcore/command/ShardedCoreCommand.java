package com.shardedcore.command;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.MessageUtil;
import com.shardedcore.util.TabCompleteHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class ShardedCoreCommand implements CommandExecutor, TabCompleter {

    private final ShardedCore plugin;

    public ShardedCoreCommand(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shardedcore.admin")) {
            MessageUtil.send(sender, plugin, "<red>You do not have permission.</red>");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                MessageUtil.send(sender, plugin, "<green>ShardedCore reloaded.</green>");
            }
            case "features" -> sendFeatures(sender);
            case "placeholders" -> sendPlaceholders(sender);
            case "help" -> sendHelp(sender);
            default -> MessageUtil.send(sender, plugin,
                    "<red>Unknown subcommand. Use <white>/shardedcore help</white>.</red>");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.send(sender, plugin, "<aqua>ShardedCore Commands</aqua>");
        MessageUtil.send(sender, plugin, "<gray>/shardedcore reload</gray> - Reload config and modules");
        MessageUtil.send(sender, plugin, "<gray>/shardedcore features</gray> - List module status");
        MessageUtil.send(sender, plugin, "<gray>/shardedcore placeholders</gray> - List core placeholders");
        MessageUtil.send(sender, plugin, "<gray>/shardedcore help</gray> - Show this help");
    }

    private void sendFeatures(CommandSender sender) {
        MessageUtil.send(sender, plugin, "<aqua>Module Status</aqua>");
        var modules = plugin.pluginConfig().getConfigurationSection("modules");
        if (modules == null) {
            MessageUtil.send(sender, plugin, "<gray>No modules configured.</gray>");
            return;
        }
        for (String key : modules.getKeys(false)) {
            boolean configEnabled = modules.getBoolean(key);
            boolean runtimeEnabled = plugin.modules().isEnabled(key);
            String status = configEnabled
                    ? (runtimeEnabled ? "<green>enabled</green>" : "<yellow>registered-off</yellow>")
                    : "<red>disabled</red>";
            MessageUtil.send(sender, plugin, "<gray>- " + key + ":</gray> " + status);
        }
    }

    private void sendPlaceholders(CommandSender sender) {
        MessageUtil.send(sender, plugin, "<aqua>Core Placeholders</aqua>");
        MessageUtil.send(sender, plugin, "<gray>%shardedcore_prefix%</gray>");
        MessageUtil.send(sender, plugin, "<gray>%shardedcore_module_<id>%</gray>");
        if (plugin.hasPlaceholderApi()) {
            MessageUtil.send(sender, plugin, "<green>PlaceholderAPI detected.</green>");
        } else {
            MessageUtil.send(sender, plugin, "<yellow>PlaceholderAPI not installed.</yellow>");
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("shardedcore.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return TabCompleteHelper.filter(List.of("reload", "features", "placeholders", "help"), args[0]);
        }
        return List.of();
    }
}

package dev.sharded.core;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class CoreCommand implements CommandExecutor {
    private final ShardedCore plugin;

    public CoreCommand(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedcore.admin")) {
            sender.sendMessage(plugin.messages().get("core.no-permission"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadAll();
            sender.sendMessage(plugin.messages().get("core.reloaded"));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /shardedcore reload"));
        return true;
    }
}

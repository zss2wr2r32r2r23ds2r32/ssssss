package dev.shardedsmp.command;

import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class GraceCommand implements CommandExecutor, TabCompleter {
    private final ShardedSMP plugin;

    public GraceCommand(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("start")) {
            sender.sendMessage(ColorUtil.color("&cUsage: /grace start"));
            return true;
        }
        if (!plugin.game().startGrace()) {
            sender.sendMessage(ColorUtil.color("&cGrace has already been started."));
            return true;
        }
        sender.sendMessage(ColorUtil.color("&aGrace has started. Players were scattered and given steak."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("start");
        }
        return List.of();
    }
}

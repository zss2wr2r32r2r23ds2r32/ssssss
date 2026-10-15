package dev.shardedsmp.command;

import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class ObsidianCommand implements CommandExecutor, TabCompleter {
    private final ShardedSMP plugin;

    public ObsidianCommand(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ColorUtil.color("&cUsage: /obsidian test"));
            return true;
        }
        if (args[0].equalsIgnoreCase("test")) {
            Player player = sender instanceof Player p ? p : null;
            if (plugin.obsidianManager().spawnTest(player)) {
                sender.sendMessage(ColorUtil.color("&aSpawned a test obsidian shard."));
            } else {
                sender.sendMessage(ColorUtil.color("&cCould not spawn obsidian. Is the overworld loaded?"));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ColorUtil.color("&fPhase: &#FF0000" + plugin.game().phase().name()));
            sender.sendMessage(ColorUtil.color("&fObsidian spawned: &e" + plugin.game().obsidianSpawned() + "&7/&e" + plugin.game().obsidianTotal()));
            sender.sendMessage(ColorUtil.color("&fObsidian found: &e" + plugin.game().obsidianFound()));
            sender.sendMessage(ColorUtil.color("&fGrace: " + (plugin.game().graceActive() ? "&aactive" : "&7inactive")));
            sender.sendMessage(ColorUtil.color("&fNether: " + (plugin.game().netherOpen() ? "&aopen" : "&clocked")));
            sender.sendMessage(ColorUtil.color("&fEnd: " + (plugin.game().endOpen() ? "&aopen" : "&clocked")));
            sender.sendMessage(ColorUtil.color("&fDiamonds: &b" + plugin.game().diamondsMined() + "&7/&b" + plugin.game().diamondsNeeded()));
            return true;
        }
        sender.sendMessage(ColorUtil.color("&cUsage: /obsidian test"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("test", "status");
        }
        return List.of();
    }
}

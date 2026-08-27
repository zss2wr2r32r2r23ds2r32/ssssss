package dev.shardedsmp.command;

import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DiamondsCommand implements CommandExecutor {
    private final ShardedSMP plugin;

    public DiamondsCommand(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage(ColorUtil.color("&bCommunity Diamonds: &f"
                + plugin.game().diamondsMined() + "&7/&f" + plugin.game().diamondsNeeded()));
        return true;
    }
}

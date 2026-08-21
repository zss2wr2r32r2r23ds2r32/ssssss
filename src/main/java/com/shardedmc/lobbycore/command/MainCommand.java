package com.shardedmc.lobbycore.command;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final ShardedLobbyCore plugin;

    public MainCommand(ShardedLobbyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedlobbycore.admin")) {
            MessageUtil.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            MessageUtil.sendRaw(sender, "&bShardedLobbyCore &7v" + plugin.getDescription().getVersion());
            MessageUtil.sendRaw(sender, "&7/shardedlobbycore reload &8- &fReload all configs");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reload();
            MessageUtil.send(sender, "reload-success");
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("reload");
        }
        return completions;
    }
}

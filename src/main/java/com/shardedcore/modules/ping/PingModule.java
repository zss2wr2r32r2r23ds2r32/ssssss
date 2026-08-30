package com.shardedcore.modules.ping;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Tabs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class PingModule extends Module implements CommandExecutor, TabCompleter {

    public PingModule(ShardedCore plugin) {
        super(plugin, "ping");
    }

    @Override
    public void enable() {
        registerCommand("ping", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            sendRawBar(sender, cfg("self", "").replace("%ping%", String.valueOf(player.getPing())));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "offline");
            return true;
        }
        sendRawBar(sender, cfg("other", "")
                .replace("%player%", target.getName())
                .replace("%ping%", String.valueOf(target.getPing())));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Tabs.players(args[0]) : List.of();
    }
}

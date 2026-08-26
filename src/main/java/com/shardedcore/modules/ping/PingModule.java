package com.shardedcore.modules.ping;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.OfflinePlayers;
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
        clearCommands();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.ping.use")) {
            send(player, "no-permission");
            return true;
        }

        Player target = player;
        if (args.length >= 1) {
            if (!player.hasPermission("shardedcore.ping.others")) {
                send(player, "no-permission-others");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                send(player, "player-not-found", "player", args[0]);
                return true;
            }
        }

        int ping = target.getPing();
        String colour = colourForPing(ping);
        if (target.equals(player)) {
            send(player, "self", "ping", String.valueOf(ping), "colour", colour);
        } else {
            send(player, "other", "player", target.getName(), "ping", String.valueOf(ping), "colour", colour);
        }
        return true;
    }

    private String colourForPing(int ping) {
        int goodBelow = config.getInt("good-below", 80);
        int okayBelow = config.getInt("okay-below", 180);
        if (ping < goodBelow) {
            return config.getString("colours.good", "&#9FFF00");
        }
        if (ping < okayBelow) {
            return config.getString("colours.okay", "&#FFBA00");
        }
        return config.getString("colours.bad", "&#FF2727");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shardedcore.ping.others")) {
            return OfflinePlayers.onlinePlayers(args[0]);
        }
        return List.of();
    }
}

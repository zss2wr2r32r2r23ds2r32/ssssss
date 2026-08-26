package com.sharded.core.modules.ping;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** /ping — show connection latency with colour-coded thresholds. */
public final class PingModule extends Module implements CommandExecutor, TabCompleter {

    public PingModule(ShardedCore plugin) {
        super(plugin, "ping");
    }

    @Override
    protected void onEnable() {
        registerCommand("ping", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.ping.use")) {
            send(player, "no-permission");
            return true;
        }

        Player target = player;
        if (args.length >= 1) {
            if (!player.hasPermission("sharded.ping.others")) {
                send(player, "no-permission-others");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                send(player, "player-not-found", "%player%", args[0]);
                return true;
            }
        }

        int ping = target.getPing();
        String colour = colourForPing(ping);
        if (target.equals(player)) {
            send(player, "self", "%ping%", String.valueOf(ping), "%colour%", colour);
        } else {
            send(player, "other", "%player%", target.getName(), "%ping%", String.valueOf(ping), "%colour%", colour);
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
        if (args.length == 1 && sender.hasPermission("sharded.ping.others")) {
            return TabCompleteHelper.onlinePlayers(args[0]);
        }
        return List.of();
    }
}

package com.sharded.core.modules.kill;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /kill [player] - kills yourself or another player. */
public final class KillModule extends Module implements CommandExecutor, TabCompleter {

    public KillModule(ShardedCore plugin) {
        super(plugin, "kill");
    }

    @Override
    protected void onEnable() {
        registerCommand("kill", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            target = player;
        } else {
            if (!sender.hasPermission("sharded.kill.others")) {
                send(sender, "no-permission");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                send(sender, "player-not-found", "%player%", args[0]);
                return true;
            }
        }
        if (!sender.hasPermission("sharded.kill.use")) {
            send(sender, "no-permission");
            return true;
        }
        target.setHealth(0);
        if (sender != target) {
            send(sender, "killed-other", "%player%", target.getName());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("sharded.kill.others")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}

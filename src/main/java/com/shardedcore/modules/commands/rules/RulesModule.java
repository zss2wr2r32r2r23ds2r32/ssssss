package com.shardedcore.modules.commands.rules;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RulesModule extends Module implements CommandExecutor {

    public RulesModule(ShardedCore plugin) {
        super(plugin, "rules");
    }

    @Override
    public void enable() {
        registerCommand("rules", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command.rules") && !player.hasPermission("sharded.command.rules")) {
            send(player, "no-permission");
            return true;
        }
        plugin.gui().open(player, "guide_rules");
        return true;
    }
}

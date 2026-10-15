package dev.sharded.core.spawn;

import dev.sharded.core.ShardedCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class SpawnCommands implements CommandExecutor {
    private final ShardedCore plugin;

    public SpawnCommands(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("core.players-only"));
            return true;
        }
        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("shardedcore.setspawn")) {
                player.sendMessage(plugin.messages().get("core.no-permission"));
                return true;
            }
            plugin.spawns().setMainSpawn(player.getLocation());
            player.sendMessage(plugin.messages().get(
                    "spawn.set",
                    "%x%", String.valueOf(player.getLocation().getBlockX()),
                    "%y%", String.valueOf(player.getLocation().getBlockY()),
                    "%z%", String.valueOf(player.getLocation().getBlockZ()),
                    "%world%", player.getWorld().getName()
            ));
            return true;
        }
        if (!player.hasPermission("shardedcore.spawn")) {
            player.sendMessage(plugin.messages().get("core.no-permission"));
            return true;
        }
        plugin.spawns().teleport(player);
        return true;
    }
}

package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.modules.SpawnModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code /setspawn <crystal|diasmp>} — also lifts the whitelist so players can join. */
public final class SetSpawnCommand extends BaseCommand {

    public SetSpawnCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.setspawn", SpawnModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length == 0) {
            plugin.messages().send(sender, "spawn.set-usage", "%label%", label);
            return;
        }
        EventMode mode = EventMode.fromId(args[0]);
        if (mode == null) {
            plugin.messages().send(sender, "spawn.unknown-mode", "%input%", args[0]);
            return;
        }

        SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
        spawnModule.setSpawn(mode, player.getLocation());

        plugin.messages().send(sender, "spawn.set",
                "%mode%", mode.id(),
                "%x%", Integer.toString(player.getLocation().getBlockX()),
                "%y%", Integer.toString(player.getLocation().getBlockY()),
                "%z%", Integer.toString(player.getLocation().getBlockZ()),
                "%world%", player.getWorld().getName());
        plugin.guis().refreshAll();
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> modes = new ArrayList<>(EventMode.values().length);
        for (EventMode mode : EventMode.values()) {
            modes.add(mode.id());
        }
        return filter(modes, args[0]);
    }
}

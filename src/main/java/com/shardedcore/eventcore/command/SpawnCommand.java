package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.modules.SpawnModule;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@code /spawn [crystal|diasmp|all]}
 *
 * <p>Naming a gamemode teleports the whole server to that gamemode's spawn;
 * {@code all} uses whichever gamemode is currently selected. With no argument
 * only the sender moves.</p>
 */
public final class SpawnCommand extends BaseCommand {

    public SpawnCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.spawn", SpawnModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);

        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return;
            }
            Location target = spawnModule.resolveActiveSpawn();
            if (target == null) {
                plugin.messages().send(sender, "spawn.not-set", "%mode%",
                        plugin.state().hasSelection() ? plugin.state().selected().id() : "none");
                return;
            }
            spawnModule.teleport(player, target);
            plugin.messages().send(sender, "spawn.teleported-self");
            return;
        }

        if (args[0].equalsIgnoreCase("all")) {
            if (!plugin.state().hasSelection()) {
                plugin.messages().send(sender, "spawn.no-selection");
                return;
            }
            teleportEveryone(sender, spawnModule, plugin.state().selected());
            return;
        }

        EventMode mode = EventMode.fromId(args[0]);
        if (mode == null) {
            plugin.messages().send(sender, "spawn.unknown-mode", "%input%", args[0]);
            return;
        }
        teleportEveryone(sender, spawnModule, mode);
    }

    private void teleportEveryone(CommandSender sender, SpawnModule spawnModule, EventMode mode) {
        Location target = spawnModule.resolveSpawn(mode);
        if (target == null) {
            plugin.messages().send(sender, "spawn.not-set", "%mode%", mode.id());
            return;
        }
        int moved = spawnModule.teleportEveryone(target);
        plugin.messages().send(sender, "spawn.teleported-all",
                "%mode%", mode.id(), "%players%", Integer.toString(moved));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        List<String> options = new ArrayList<>(EventMode.values().length + 1);
        for (EventMode mode : EventMode.values()) {
            options.add(mode.id());
        }
        options.add("all");
        return filter(options, args[0]);
    }
}

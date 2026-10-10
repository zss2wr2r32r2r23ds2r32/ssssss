package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.modules.SettingsModule;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** {@code /settings [crystal|diasmp]} — opens the selector or a mode board directly. */
public final class SettingsCommand extends BaseCommand {

    public SettingsCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.settings", SettingsModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length > 0) {
            EventMode mode = EventMode.fromId(args[0]);
            if (mode == null) {
                plugin.messages().send(sender, "spawn.unknown-mode", "%input%", args[0]);
                return;
            }
            plugin.guis().mode(mode).open(player);
            return;
        }
        plugin.guis().selector().open(player);
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

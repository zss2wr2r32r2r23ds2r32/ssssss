package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.modules.GameModule;
import org.bukkit.command.CommandSender;

/**
 * {@code /end} — turns the whitelist back on and sends everyone to the lobby.
 */
public final class EndCommand extends BaseCommand {

    public EndCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.end", GameModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        plugin.modules().byType(GameModule.class).end();
        plugin.messages().send(sender, "game.end-triggered");
    }
}

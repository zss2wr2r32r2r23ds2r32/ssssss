package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.modules.GameModule;
import org.bukkit.command.CommandSender;

/**
 * {@code /start} — runs the pre-event countdown, then unlocks PvP, damage and
 * building, and turns the whitelist on.
 */
public final class StartCommand extends BaseCommand {

    public StartCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.start", GameModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        GameModule game = plugin.modules().byType(GameModule.class);

        if (args.length > 0 && args[0].equalsIgnoreCase("now")) {
            game.unlock();
            plugin.messages().send(sender, "game.start-immediate");
            return;
        }
        if (!game.start()) {
            plugin.messages().send(sender, plugin.state().running()
                    ? "game.already-running" : "game.start-failed");
            return;
        }
        plugin.messages().send(sender, "game.start-queued");
    }

    @Override
    protected java.util.List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? filter(java.util.List.of("now"), args[0]) : java.util.List.of();
    }
}

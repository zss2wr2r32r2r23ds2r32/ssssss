package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.modules.AnnounceModule;
import org.bukkit.command.CommandSender;

/** {@code /announce <text>} */
public final class AnnounceCommand extends BaseCommand {

    public AnnounceCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.announce", AnnounceModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "announce.usage", "%label%", label);
            return;
        }
        String text = join(args, 0);
        plugin.modules().byType(AnnounceModule.class).announce(text);
        plugin.messages().send(sender, "announce.sent", "%text%", text);
    }
}

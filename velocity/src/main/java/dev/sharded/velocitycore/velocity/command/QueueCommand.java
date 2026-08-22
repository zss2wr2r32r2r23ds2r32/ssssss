package dev.sharded.velocitycore.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.sharded.velocitycore.common.ServerState;
import dev.sharded.velocitycore.velocity.config.PluginConfig;
import dev.sharded.velocitycore.velocity.queue.QueueManager;
import dev.sharded.velocitycore.velocity.status.ServerStatusManager;
import dev.sharded.velocitycore.velocity.util.LegacyText;

import java.util.List;
import java.util.Locale;

public final class QueueCommand implements SimpleCommand {

    private final QueueManager queueManager;
    private final PluginConfig config;

    public QueueCommand(QueueManager queueManager, PluginConfig config) {
        this.queueManager = queueManager;
        this.config = config;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(LegacyText.parse("&#FF0000This command can only be used by players."));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            queueManager.leaveQueue(player);
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage("&#8AFF00You left the queue.")));
            return;
        }

        String target = args.length > 0
                ? args[0].toLowerCase(Locale.ROOT)
                : config.defaultQueueServer();

        ServerState state = queueManager.statusManager().getState(target);
        if (state == ServerState.MAINTENANCE) {
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                    "&#FF0000" + target + " is currently in maintenance."
            )));
            return;
        }

        if (!queueManager.joinQueue(player, target)) {
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                    "&#FF0000Unknown server &#4498DB" + target + "&#FF0000. Available: survival, events, diamondsmp"
            )));
            return;
        }

        if (queueManager.position(player.getUniqueId()) > 0) {
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                    "&#8AFF00You joined the queue for &#4498DB" + target
                            + "&#8AFF00. Position: &#FFFFFF#" + queueManager.position(player.getUniqueId())
            )));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return List.of("survival", "events", "diamondsmp", "leave");
        }
        return List.of();
    }
}

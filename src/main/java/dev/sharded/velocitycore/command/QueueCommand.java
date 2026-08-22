package dev.sharded.velocitycore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.sharded.velocitycore.ServerState;
import dev.sharded.velocitycore.config.PluginConfig;
import dev.sharded.velocitycore.queue.QueueManager;
import dev.sharded.velocitycore.util.LegacyText;
import dev.sharded.velocitycore.util.ServerResolver;

import java.util.List;
import java.util.Locale;

public final class QueueCommand implements SimpleCommand {

    private final ProxyServer proxy;
    private final QueueManager queueManager;
    private final PluginConfig config;

    public QueueCommand(ProxyServer proxy, QueueManager queueManager, PluginConfig config) {
        this.proxy = proxy;
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
                ? args[0]
                : config.defaultQueueServer();

        if (ServerResolver.find(proxy, target).isEmpty()) {
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                    "&#FF0000Unknown server &#4498DB" + target + "&#FF0000. Available: "
                            + ServerResolver.availableServers(proxy)
            )));
            return;
        }

        String canonical = ServerResolver.canonicalName(proxy, target);

        ServerState state = queueManager.statusManager().getState(canonical);
        if (state == ServerState.MAINTENANCE) {
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                    "&#FF0000" + canonical + " is currently in maintenance."
            )));
            return;
        }

        boolean joined = queueManager.joinQueue(player, canonical);
        if (!joined) {
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                    "&#FF0000Unable to queue for &#4498DB" + canonical + "&#FF0000."
            )));
            return;
        }

        if (queueManager.isQueued(player.getUniqueId())) {
            player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                    "&#8AFF00You joined the queue for &#4498DB" + canonical
                            + "&#8AFF00. Position: &#FFFFFF#" + queueManager.position(player.getUniqueId())
            )));
            return;
        }

        player.sendMessage(LegacyText.parse(queueManager.formatQueueMessage(
                "&#8AFF00Connecting you to &#4498DB" + canonical + "&#8AFF00..."
        )));
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return config.trackedServers();
        }
        return List.of();
    }
}

package dev.sharded.velocitycore.queue;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.sharded.velocitycore.ServerState;
import dev.sharded.velocitycore.config.PluginConfig;
import dev.sharded.velocitycore.util.LegacyText;
import dev.sharded.velocitycore.util.ServerResolver;
import net.kyori.adventure.text.Component;

import java.util.List;

public final class ServerConnectService {

    private final ProxyServer proxy;
    private final QueueManager queueManager;
    private final PluginConfig config;

    public ServerConnectService(ProxyServer proxy, QueueManager queueManager, PluginConfig config) {
        this.proxy = proxy;
        this.queueManager = queueManager;
        this.config = config;
    }

    public List<Component> connect(Player player, String target) {
        String resolvedTarget = target == null || target.isBlank()
                ? config.defaultQueueServer()
                : target;

        if (ServerResolver.find(proxy, resolvedTarget).isEmpty()) {
            return List.of(queueManager.format(
                    config.queueColors().error() + "Unknown server "
                            + config.queueColors().accent() + resolvedTarget
                            + config.queueColors().error() + ". Available: "
                            + config.queueColors().accent() + String.join(", ", config.queueServers())
            ));
        }

        String canonical = ServerResolver.canonicalName(proxy, resolvedTarget);

        if (!config.isLobby(canonical)
                && queueManager.statusManager().getState(canonical) == ServerState.MAINTENANCE) {
            return List.of(queueManager.format(
                    config.queueColors().error() + canonical + " is currently in maintenance."
            ));
        }

        if (config.isLobby(canonical) && !queueManager.statusManager().isReachable(canonical)) {
            return List.of(queueManager.format(
                    config.queueColors().error() + "Lobby is currently offline."
            ));
        }

        boolean joined = queueManager.joinQueue(player, canonical);
        if (!joined) {
            return List.of(queueManager.format(
                    config.queueColors().error() + "Unable to queue for "
                            + config.queueColors().accent() + canonical
                            + config.queueColors().error() + "."
            ));
        }

        if (queueManager.isQueued(player.getUniqueId())) {
            return List.of(queueManager.format(
                    config.queueColors().success() + "You joined the queue for "
                            + queueManager.serverColor(canonical) + "&n" + canonical + "&r"
                            + config.queueColors().success() + ". Position: "
                            + config.queueColors().position() + "#" + queueManager.position(player.getUniqueId())
            ));
        }

        return List.of(queueManager.format(
                config.queueColors().success() + "Connecting you to "
                        + queueManager.serverColor(canonical) + "&n" + canonical + "&r"
                        + config.queueColors().success() + "..."
        ));
    }

    public void leave(Player player) {
        queueManager.leaveQueue(player);
        player.sendMessage(queueManager.format(config.queueColors().success() + "You left the queue."));
    }

    public void sendPlayersOnly(CommandSource source) {
        source.sendMessage(LegacyText.parse(config.queueColors().error() + "This command can only be used by players."));
    }

    public List<String> suggestions() {
        return config.queueServers();
    }

    public Component usageMessage() {
        return queueManager.format(
                config.queueColors().accent() + "Usage: /server <server>"
                        + config.queueColors().error() + " — Available: "
                        + config.queueColors().accent() + String.join(", ", config.queueServers())
        );
    }
}

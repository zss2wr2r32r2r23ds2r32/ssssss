package dev.sharded.velocitycore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.sharded.velocitycore.config.PluginConfig;
import dev.sharded.velocitycore.queue.QueueManager;
import dev.sharded.velocitycore.queue.ServerConnectService;

import java.util.List;

public final class ServerCommand implements SimpleCommand {

    private final ServerConnectService connectService;

    public ServerCommand(ProxyServer proxy, QueueManager queueManager, PluginConfig config) {
        this.connectService = new ServerConnectService(proxy, queueManager, config);
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            connectService.sendPlayersOnly(invocation.source());
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            player.sendMessage(connectService.usageMessage());
            return;
        }

        connectService.connect(player, args[0]).forEach(player::sendMessage);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return connectService.suggestions();
    }
}

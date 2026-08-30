package dev.sharded.velocitycore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.sharded.velocitycore.queue.ServerConnectService;

import java.util.List;

public final class ServerCommand implements SimpleCommand {

    private final ServerConnectService connectService;

    public ServerCommand(ServerConnectService connectService) {
        this.connectService = connectService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            connectService.sendPlayersOnly(invocation.source());
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
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
        if (invocation.arguments().length <= 1) {
            return connectService.suggestions();
        }
        return List.of();
    }
}

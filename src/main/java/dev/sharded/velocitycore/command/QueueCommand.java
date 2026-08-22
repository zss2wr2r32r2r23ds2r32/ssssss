package dev.sharded.velocitycore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.sharded.velocitycore.queue.ServerConnectService;

import java.util.List;

public final class QueueCommand implements SimpleCommand {

    private final ServerConnectService connectService;

    public QueueCommand(ServerConnectService connectService) {
        this.connectService = connectService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            connectService.sendPlayersOnly(invocation.source());
            return;
        }

        String[] args = invocation.arguments();
        if (args.length > 0 && args[0].equalsIgnoreCase("leave")) {
            connectService.leave(player);
            return;
        }

        String target = args.length > 0 ? args[0] : null;
        connectService.connect(player, target).forEach(player::sendMessage);
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

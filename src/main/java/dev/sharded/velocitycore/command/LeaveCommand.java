package dev.sharded.velocitycore.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.sharded.velocitycore.queue.ServerConnectService;

import java.util.List;

public final class LeaveCommand implements SimpleCommand {

    private final ServerConnectService connectService;

    public LeaveCommand(ServerConnectService connectService) {
        this.connectService = connectService;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            connectService.sendPlayersOnly(invocation.source());
            return;
        }

        connectService.leave(player);
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return true;
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}

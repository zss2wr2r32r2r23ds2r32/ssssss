package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.proxy.Player;
import dev.sharded.velocitycore.queue.ServerConnectService;

public final class ServerCommandListener {

    private final ServerConnectService connectService;

    public ServerCommandListener(ServerConnectService connectService) {
        this.connectService = connectService;
    }

    @Subscribe(order = PostOrder.FIRST)
    public void onCommandExecute(CommandExecuteEvent event) {
        String raw = event.getCommand().trim();
        if (raw.isEmpty()) {
            return;
        }

        String[] parts = raw.split("\\s+");
        if (!parts[0].equalsIgnoreCase("server")) {
            return;
        }

        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        event.setResult(CommandExecuteEvent.CommandResult.denied());

        if (parts.length < 2) {
            player.sendMessage(connectService.usageMessage());
            return;
        }

        connectService.connect(player, parts[1]).forEach(player::sendMessage);
    }
}

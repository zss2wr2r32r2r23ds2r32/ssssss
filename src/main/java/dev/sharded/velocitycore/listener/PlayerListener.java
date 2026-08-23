package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import dev.sharded.velocitycore.queue.QueueManager;
import dev.sharded.velocitycore.status.StatusSyncService;
import dev.sharded.velocitycore.status.WhitelistRequestService;

public final class PlayerListener {

    private final QueueManager queueManager;
    private final StatusSyncService statusSyncService;
    private final WhitelistRequestService whitelistRequestService;

    public PlayerListener(
            QueueManager queueManager,
            StatusSyncService statusSyncService,
            WhitelistRequestService whitelistRequestService
    ) {
        this.queueManager = queueManager;
        this.statusSyncService = statusSyncService;
        this.whitelistRequestService = whitelistRequestService;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        queueManager.leaveQueue(event.getPlayer());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        whitelistRequestService.requestAll();
        statusSyncService.sendToPlayer(event.getPlayer());
    }
}

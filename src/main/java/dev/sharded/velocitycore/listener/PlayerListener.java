package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import dev.sharded.velocitycore.queue.QueueManager;
import dev.sharded.velocitycore.status.StatusSyncService;

public final class PlayerListener {

    private final QueueManager queueManager;
    private final StatusSyncService statusSyncService;

    public PlayerListener(QueueManager queueManager, StatusSyncService statusSyncService) {
        this.queueManager = queueManager;
        this.statusSyncService = statusSyncService;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        queueManager.leaveQueue(event.getPlayer());
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        // Use cached status only — do not re-ping or re-request on every transfer.
        statusSyncService.sendToPlayer(event.getPlayer());
    }
}

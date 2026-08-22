package dev.sharded.velocitycore.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import dev.sharded.velocitycore.velocity.queue.QueueManager;

public final class PlayerDisconnectListener {

    private final QueueManager queueManager;

    public PlayerDisconnectListener(QueueManager queueManager) {
        this.queueManager = queueManager;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        queueManager.leaveQueue(event.getPlayer());
    }
}

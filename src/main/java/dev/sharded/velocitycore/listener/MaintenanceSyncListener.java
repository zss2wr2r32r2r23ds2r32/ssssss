package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.sharded.velocitycore.common.PluginChannels;
import dev.sharded.velocitycore.status.MaintenanceMessages;
import dev.sharded.velocitycore.status.NetworkMotdState;

public final class MaintenanceSyncListener {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannels.MAINTENANCE_CHANNEL);

    private final NetworkMotdState networkMotdState;

    public MaintenanceSyncListener(NetworkMotdState networkMotdState) {
        this.networkMotdState = networkMotdState;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        try {
            MaintenanceMessages.Sync sync = MaintenanceMessages.decode(event.getData());
            networkMotdState.update(sync);
        } catch (Exception ignored) {
            // Ignore malformed maintenance sync payloads.
        }
    }
}

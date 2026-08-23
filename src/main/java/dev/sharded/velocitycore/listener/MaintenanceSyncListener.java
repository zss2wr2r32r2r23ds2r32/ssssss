package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.sharded.velocitycore.common.PluginChannels;
import dev.sharded.velocitycore.status.MaintenanceMessages;
import dev.sharded.velocitycore.status.NetworkMaintenanceState;
import dev.sharded.velocitycore.util.LegacyText;

public final class MaintenanceSyncListener {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannels.MAINTENANCE_CHANNEL);

    private final NetworkMaintenanceState networkMaintenanceState;

    public MaintenanceSyncListener(NetworkMaintenanceState networkMaintenanceState) {
        this.networkMaintenanceState = networkMaintenanceState;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        try {
            MaintenanceMessages.Sync sync = MaintenanceMessages.decode(event.getData());
            networkMaintenanceState.update(
                    sync.enabled(),
                    LegacyText.parse(sync.maintenanceMotd()),
                    sync.versionText(),
                    sync.protocolVersion()
            );
        } catch (Exception ignored) {
            // Ignore malformed maintenance sync payloads.
        }
    }
}

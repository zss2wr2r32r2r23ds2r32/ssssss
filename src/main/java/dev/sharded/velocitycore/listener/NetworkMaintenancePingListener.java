package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import dev.sharded.velocitycore.status.NetworkMaintenanceState;

public final class NetworkMaintenancePingListener {

    private final NetworkMaintenanceState networkMaintenanceState;

    public NetworkMaintenancePingListener(NetworkMaintenanceState networkMaintenanceState) {
        this.networkMaintenanceState = networkMaintenanceState;
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        if (!networkMaintenanceState.isEnabled()) {
            return;
        }

        ServerPing original = event.getPing();
        event.setPing(original.asBuilder()
                .description(networkMaintenanceState.maintenanceMotd())
                .onlinePlayers(0)
                .maximumPlayers(0)
                .clearSamplePlayers()
                .version(new ServerPing.Version(
                        networkMaintenanceState.protocolVersion(),
                        networkMaintenanceState.versionText()
                ))
                .build());
    }
}

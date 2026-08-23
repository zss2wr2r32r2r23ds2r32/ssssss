package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import dev.sharded.velocitycore.motd.ServerIconService;
import dev.sharded.velocitycore.status.NetworkMotdState;
import dev.sharded.velocitycore.util.LegacyText;

public final class NetworkMotdPingListener {

    private final NetworkMotdState networkMotdState;
    private final ServerIconService iconService;

    public NetworkMotdPingListener(NetworkMotdState networkMotdState, ServerIconService iconService) {
        this.networkMotdState = networkMotdState;
        this.iconService = iconService;
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        ServerPing original = event.getPing();
        ServerPing.Builder builder = original.asBuilder()
                .description(networkMotdState.activeMotd());

        if (networkMotdState.isMaintenanceEnabled()) {
            builder.onlinePlayers(0)
                    .maximumPlayers(0)
                    .clearSamplePlayers()
                    .version(new ServerPing.Version(
                            networkMotdState.protocolVersion(),
                            LegacyText.convertSectionHex(networkMotdState.versionText()).replace('&', '§')
                    ));
        }

        var favicon = iconService.resolve(networkMotdState.activeIcon());
        if (favicon != null) {
            builder.favicon(favicon);
        }

        event.setPing(builder.build());
    }
}

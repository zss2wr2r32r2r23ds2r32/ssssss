package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import dev.sharded.velocitycore.motd.HoverLines;
import dev.sharded.velocitycore.motd.ServerIconService;
import dev.sharded.velocitycore.status.NetworkMotdState;
import dev.sharded.velocitycore.util.LegacyText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
                .description(networkMotdState.motd());

        if (networkMotdState.isMaintenanceEnabled()) {
            builder.onlinePlayers(0)
                    .maximumPlayers(0)
                    .clearSamplePlayers()
                    .version(new ServerPing.Version(
                            networkMotdState.protocolVersion(),
                            LegacyText.convertSectionHex(networkMotdState.versionText()).replace('&', '§')
                    ));
        } else {
            int online = original.getPlayers().map(ServerPing.Players::getOnline).orElse(0);
            int max = original.getPlayers().map(ServerPing.Players::getMax).orElse(0);
            builder.onlinePlayers(online).maximumPlayers(max);
            builder.version(resolveOnlineVersion(original.getVersion()));

            if (networkMotdState.hoverEnabled() && !networkMotdState.hoverMessages().isEmpty()) {
                builder.clearSamplePlayers();
                List<ServerPing.SamplePlayer> samples = new ArrayList<>();
                int onlinePlayers = online;
                int maxPlayers = max;
                for (String raw : networkMotdState.hoverMessages()) {
                    if (raw == null) {
                        continue;
                    }
                    samples.add(new ServerPing.SamplePlayer(
                            HoverLines.format(raw, onlinePlayers, maxPlayers),
                            UUID.randomUUID()
                    ));
                }
                if (!samples.isEmpty()) {
                    builder.samplePlayers(samples.toArray(new ServerPing.SamplePlayer[0]));
                }
            }

            var favicon = iconService.resolve(networkMotdState.icon());
            if (favicon != null) {
                builder.favicon(favicon);
            }
        }

        event.setPing(builder.build());
    }

    private ServerPing.Version resolveOnlineVersion(ServerPing.Version original) {
        if (original.getProtocol() >= 0 && !isMaintenanceVersionText(original.getName())) {
            return original;
        }
        return new ServerPing.Version(
                networkMotdState.onlineProtocolVersion(),
                LegacyText.convertSectionHex(networkMotdState.onlineVersionText()).replace('&', '§')
        );
    }

    private boolean isMaintenanceVersionText(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String normalized = LegacyText.convertSectionHex(networkMotdState.versionText())
                .replace('&', '§')
                .toLowerCase(Locale.ROOT);
        return name.replace('§', '&').toLowerCase(Locale.ROOT).equals(normalized.replace('§', '&'))
                || name.equalsIgnoreCase("Maintenance")
                || name.equalsIgnoreCase("MAINTENANCE");
    }
}

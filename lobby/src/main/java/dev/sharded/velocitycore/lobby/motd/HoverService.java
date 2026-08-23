package dev.sharded.velocitycore.lobby.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.sharded.velocitycore.lobby.config.MotdConfig;

import java.util.UUID;

public final class HoverService {

    public void apply(PaperServerListPingEvent event, MotdConfig config) {
        if (!config.hoverEnabled() || config.hoverMessages().isEmpty()) {
            return;
        }

        event.setHidePlayers(false);
        event.getListedPlayers().clear();

        int online = event.getNumPlayers();
        int max = event.getMaxPlayers();

        for (String raw : config.hoverMessages()) {
            if (raw == null) {
                continue;
            }
            String legacy = HoverLines.format(raw, online, max);
            event.getListedPlayers().add(
                    new PaperServerListPingEvent.ListedPlayerInfo(legacy, UUID.randomUUID())
            );
        }
    }
}

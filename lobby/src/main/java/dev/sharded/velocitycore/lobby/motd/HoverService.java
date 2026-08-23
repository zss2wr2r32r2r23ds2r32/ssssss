package dev.sharded.velocitycore.lobby.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.sharded.velocitycore.lobby.config.MotdConfig;
import dev.sharded.velocitycore.lobby.util.TextParser;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

public final class HoverService {

    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    public void apply(PaperServerListPingEvent event, MotdConfig config) {
        if (!config.hoverEnabled() || config.hoverMessages().isEmpty()) {
            return;
        }

        event.setHidePlayers(false);
        event.getListedPlayers().clear();

        int online = event.getNumPlayers();
        int max = event.getMaxPlayers();

        for (String raw : config.hoverMessages()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String replaced = raw
                    .replace("{online_players}", String.valueOf(online))
                    .replace("{max_players}", String.valueOf(max));
            String legacy = LEGACY_SECTION.serialize(TextParser.parse(replaced));
            if (legacy.length() > 40) {
                legacy = legacy.substring(0, 40);
            }
            event.getListedPlayers().add(
                    new PaperServerListPingEvent.ListedPlayerInfo(legacy, UUID.randomUUID())
            );
        }
    }
}

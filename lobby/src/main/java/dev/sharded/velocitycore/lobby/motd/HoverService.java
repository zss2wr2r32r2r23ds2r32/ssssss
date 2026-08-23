package dev.sharded.velocitycore.lobby.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.sharded.velocitycore.lobby.config.MotdConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.server.ServerListPingEvent;

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
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String replaced = raw
                    .replace("{online_players}", String.valueOf(online))
                    .replace("{max_players}", String.valueOf(max));
            String plain = PlainTextComponentSerializer.plainText()
                    .serialize(dev.sharded.velocitycore.lobby.util.TextParser.parse(replaced));
            if (plain.length() > 40) {
                plain = plain.substring(0, 40);
            }
            event.getListedPlayers().add(
                    new PaperServerListPingEvent.ListedPlayerInfo(plain, UUID.randomUUID())
            );
        }
    }
}

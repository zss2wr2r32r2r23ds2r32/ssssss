package dev.sharded.velocitycore.lobby.maintenance;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.sharded.velocitycore.lobby.config.LobbySettings;
import dev.sharded.velocitycore.lobby.util.LegacyText;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class MaintenancePingListener implements Listener {

    private final MaintenanceManager maintenanceManager;
    private final LobbySettings settings;

    public MaintenancePingListener(MaintenanceManager maintenanceManager, LobbySettings settings) {
        this.maintenanceManager = maintenanceManager;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(PaperServerListPingEvent event) {
        if (maintenanceManager.isEnabled()) {
            event.motd(LegacyText.parse(settings.maintenanceMotd()));
            event.setVersion(settings.serverListVersionText());
            event.setProtocolVersion(settings.serverListProtocolVersion());
            event.setHidePlayers(true);
            event.setNumPlayers(0);
            event.setMaxPlayers(0);
            return;
        }

        event.motd(LegacyText.parse(settings.motd()));
    }
}

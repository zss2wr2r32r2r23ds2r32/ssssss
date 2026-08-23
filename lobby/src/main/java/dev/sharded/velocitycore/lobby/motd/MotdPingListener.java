package dev.sharded.velocitycore.lobby.motd;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import dev.sharded.velocitycore.lobby.maintenance.MaintenanceManager;
import dev.sharded.velocitycore.lobby.util.TextParser;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.util.CachedServerIcon;

public final class MotdPingListener implements Listener {

    private final MaintenanceManager maintenanceManager;
    private final MotdService motdService;
    private final ServerIconService iconService;
    private final HoverService hoverService;

    public MotdPingListener(
            MaintenanceManager maintenanceManager,
            MotdService motdService,
            ServerIconService iconService,
            HoverService hoverService
    ) {
        this.maintenanceManager = maintenanceManager;
        this.motdService = motdService;
        this.iconService = iconService;
        this.hoverService = hoverService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(PaperServerListPingEvent event) {
        boolean maintenance = maintenanceManager.isEnabled();
        MotdService.ResolvedMotd resolved = maintenance
                ? motdService.resolveMaintenance()
                : motdService.resolveDefault();

        event.motd(resolved.motd());

        if (maintenance) {
            event.setVersion(TextParser.convertSectionHex(motdService.config().protocolTextValue()).replace('&', '§'));
            event.setProtocolVersion(motdService.config().protocolVersion());
            event.setHidePlayers(true);
            event.setNumPlayers(0);
            event.setMaxPlayers(0);
            event.getListedPlayers().clear();
        } else {
            hoverService.apply(event, motdService.config());
            applyDefaultIcon(event);
        }
    }

    private void applyDefaultIcon(PaperServerListPingEvent event) {
        if (!motdService.config().serverIconEnabled()) {
            return;
        }
        CachedServerIcon icon = iconService.resolve(motdService.config().serverIconImage());
        if (icon != null) {
            event.setServerIcon(icon);
        }
    }
}

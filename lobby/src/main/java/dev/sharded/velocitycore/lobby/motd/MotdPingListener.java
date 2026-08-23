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

    public MotdPingListener(
            MaintenanceManager maintenanceManager,
            MotdService motdService,
            ServerIconService iconService
    ) {
        this.maintenanceManager = maintenanceManager;
        this.motdService = motdService;
        this.iconService = iconService;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(PaperServerListPingEvent event) {
        MotdService.ResolvedMotd resolved = maintenanceManager.isEnabled()
                ? motdService.resolveMaintenance()
                : motdService.resolveDefault();

        event.motd(resolved.motd());

        if (resolved.maintenance()) {
            event.setVersion(TextParser.convertSectionHex(resolved.versionText()).replace('&', '§'));
            event.setProtocolVersion(resolved.protocolVersion());
            event.setHidePlayers(true);
            event.setNumPlayers(0);
            event.setMaxPlayers(0);
        }

        applyIcon(event, resolved.icon());
    }

    private void applyIcon(PaperServerListPingEvent event, String iconName) {
        CachedServerIcon icon = iconName == null || iconName.isBlank()
                ? iconService.resolveDefault(motdService.config())
                : iconService.resolve(iconName);
        if (icon != null) {
            event.setServerIcon(icon);
        }
    }
}

package dev.sharded.velocitycore.status;

import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.text.Component;

public final class NetworkMotdState {

    private volatile boolean maintenanceEnabled;
    private volatile Component normalMotd = LegacyText.parse("&#8AFF00&lSHARDEDMC");
    private volatile String normalIcon = "";
    private volatile Component maintenanceMotd = LegacyText.parse("&#FF0000&lMAINTENANCE");
    private volatile String maintenanceIcon = "";
    private volatile String versionText = "Maintenance";
    private volatile int protocolVersion = -1;

    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public Component activeMotd() {
        return maintenanceEnabled ? maintenanceMotd : normalMotd;
    }

    public String activeIcon() {
        return maintenanceEnabled ? maintenanceIcon : normalIcon;
    }

    public String versionText() {
        return versionText;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public void update(MaintenanceMessages.Sync sync) {
        this.maintenanceEnabled = sync.maintenanceEnabled();
        if (sync.normalMotd() != null && !sync.normalMotd().equals(Component.empty())) {
            this.normalMotd = sync.normalMotd();
        }
        this.normalIcon = sync.normalIcon() == null ? "" : sync.normalIcon();
        if (sync.maintenanceMotd() != null) {
            this.maintenanceMotd = sync.maintenanceMotd();
        }
        this.maintenanceIcon = sync.maintenanceIcon() == null ? "" : sync.maintenanceIcon();
        if (sync.versionText() != null && !sync.versionText().isBlank()) {
            this.versionText = sync.versionText();
        }
        this.protocolVersion = sync.protocolVersion();
    }
}

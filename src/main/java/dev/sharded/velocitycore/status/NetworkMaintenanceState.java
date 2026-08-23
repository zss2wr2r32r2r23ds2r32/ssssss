package dev.sharded.velocitycore.status;

import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.text.Component;

public final class NetworkMaintenanceState {

    private volatile boolean enabled;
    private volatile Component maintenanceMotd = LegacyText.parse("&#FF0000&lMAINTENANCE");
    private volatile String versionText = "Maintenance";
    private volatile int protocolVersion = -1;

    public boolean isEnabled() {
        return enabled;
    }

    public Component maintenanceMotd() {
        return maintenanceMotd;
    }

    public String versionText() {
        return versionText;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public void update(boolean enabled, Component maintenanceMotd, String versionText, int protocolVersion) {
        this.enabled = enabled;
        if (maintenanceMotd != null) {
            this.maintenanceMotd = maintenanceMotd;
        }
        if (versionText != null && !versionText.isBlank()) {
            this.versionText = versionText;
        }
        this.protocolVersion = protocolVersion;
    }
}

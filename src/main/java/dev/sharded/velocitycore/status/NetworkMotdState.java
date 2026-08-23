package dev.sharded.velocitycore.status;

import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public final class NetworkMotdState {

    private volatile boolean maintenanceEnabled;
    private volatile Component motd = LegacyText.parse("&#8AFF00&lSHARDEDMC");
    private volatile String icon = "";
    private volatile String versionText = "Maintenance";
    private volatile int protocolVersion = -1;
    private volatile boolean hoverEnabled = true;
    private volatile List<String> hoverMessages = List.of();

    public boolean isMaintenanceEnabled() {
        return maintenanceEnabled;
    }

    public Component motd() {
        return motd;
    }

    public String icon() {
        return icon;
    }

    public String versionText() {
        return versionText;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public boolean hoverEnabled() {
        return hoverEnabled;
    }

    public List<String> hoverMessages() {
        return hoverMessages;
    }

    public void update(MaintenanceMessages.Sync sync) {
        this.maintenanceEnabled = sync.maintenanceEnabled();
        if (sync.motd() != null && !sync.motd().equals(Component.empty())) {
            this.motd = sync.motd();
        }
        this.icon = sync.icon() == null ? "" : sync.icon();
        if (sync.versionText() != null && !sync.versionText().isBlank()) {
            this.versionText = sync.versionText();
        }
        this.protocolVersion = sync.protocolVersion();
        this.hoverEnabled = sync.hoverEnabled();
        this.hoverMessages = sync.hoverMessages() == null
                ? List.of()
                : new ArrayList<>(sync.hoverMessages());
    }
}

package dev.sharded.velocitycore.status;

import dev.sharded.velocitycore.config.MotdCenter;
import dev.sharded.velocitycore.config.ProxyMotdConfig;
import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;

public final class NetworkMotdState {

    private volatile boolean maintenanceEnabled;
    private volatile Component motd = defaultMotd();
    private volatile String icon = "";
    private volatile String versionText = "Maintenance";
    private volatile int protocolVersion = -1;
    private volatile boolean hoverEnabled = true;
    private volatile List<String> hoverMessages = defaultHover();

    public void applyDefaults(ProxyMotdConfig config) {
        this.motd = LegacyText.parseLines(config.motdLines());
        this.hoverEnabled = config.hoverEnabled();
        this.hoverMessages = new ArrayList<>(config.hoverMessages());
        this.versionText = config.maintenanceVersionText();
        this.protocolVersion = config.maintenanceProtocolVersion();
        this.maintenanceEnabled = false;
    }

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

    private static Component defaultMotd() {
        return LegacyText.parseLines(List.of(
                MotdCenter.center("&#AD4EFF&lSHARDEDMC &8▷ &7[1.21+]", 48),
                MotdCenter.center("&#FFE300⚓ &#FFE300&lSEASON 1 SOON &#FFE300⚓", 48)
        ));
    }

    private static List<String> defaultHover() {
        return List.of(
                "",
                "&#AD4EFF&lSHARDEDMC &#AD4EFF&lNetwork &8| &7[1.21+]",
                "",
                "&#AD4EFFɪɴꜰᴏʀᴍᴀᴛɪᴏɴ:",
                "&#FF005D⚓ &fDiscord &8▷ &#FF005Dᴅɪsᴄᴏʀᴅ.ɢɢ/shardedmc",
                "&#9FFF00⛨ &fStore &8▷ &#9FFF00ᴄᴏᴍɪɴɢ sᴏᴏɴ",
                "",
                "&#AD4EFF☀ &fPlay with &#AD4EFF&n{online_players}&f other players"
        );
    }
}

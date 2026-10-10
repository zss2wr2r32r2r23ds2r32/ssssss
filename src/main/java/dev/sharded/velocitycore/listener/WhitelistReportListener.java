package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import dev.sharded.velocitycore.common.PluginChannels;
import dev.sharded.velocitycore.status.ServerStatusManager;
import dev.sharded.velocitycore.status.StatusSyncService;
import dev.sharded.velocitycore.status.WhitelistMessages;

public final class WhitelistReportListener {

    private static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(PluginChannels.WHITELIST_CHANNEL);

    private final ServerStatusManager statusManager;
    private final StatusSyncService statusSyncService;

    public WhitelistReportListener(ServerStatusManager statusManager, StatusSyncService statusSyncService) {
        this.statusManager = statusManager;
        this.statusSyncService = statusSyncService;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(CHANNEL)) {
            return;
        }

        try {
            if (WhitelistMessages.isRequest(event.getData())) {
                return;
            }
            WhitelistMessages.Report report = WhitelistMessages.decode(event.getData());
            if (statusManager.updateWhitelistReport(report.serverName(), report.whitelisted())) {
                statusSyncService.broadcastNow();
            }
        } catch (Exception ignored) {
            // Ignore malformed reports.
        }
    }
}

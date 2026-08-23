package dev.sharded.velocitycore.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import dev.sharded.velocitycore.status.ServerStatusManager;
import dev.sharded.velocitycore.status.StatusSyncService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class WhitelistListener {

    private final ServerStatusManager statusManager;
    private final StatusSyncService statusSyncService;

    public WhitelistListener(ServerStatusManager statusManager, StatusSyncService statusSyncService) {
        this.statusManager = statusManager;
        this.statusSyncService = statusSyncService;
    }

    @Subscribe
    public void onKickedFromServer(KickedFromServerEvent event) {
        if (!statusManager.whitelistAsMaintenance()) {
            return;
        }

        String reason = event.getServerKickReason()
                .map(component -> PlainTextComponentSerializer.plainText().serialize(component).toLowerCase())
                .orElse("");

        if (!isWhitelistReason(reason)) {
            return;
        }

        String serverName = event.getServer().getServerInfo().getName();
        if (statusManager.markWhitelisted(serverName)) {
            statusSyncService.broadcastNow();
        }
    }

    private static boolean isWhitelistReason(String reason) {
        return reason.contains("whitelist")
                || reason.contains("white-list")
                || reason.contains("not on the whitelist")
                || reason.contains("not whitelisted");
    }
}

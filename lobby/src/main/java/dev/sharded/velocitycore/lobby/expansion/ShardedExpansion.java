package dev.sharded.velocitycore.lobby.expansion;

import dev.sharded.velocitycore.lobby.sync.StatusCache;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public final class ShardedExpansion extends PlaceholderExpansion {

    private final StatusCache statusCache;

    public ShardedExpansion(StatusCache statusCache) {
        this.statusCache = statusCache;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "shardedvelocitycore";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Sharded";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.3";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String normalized = params.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("status_")) {
            String server = normalized.substring("status_".length());
            if (server.equals("diamondsmp")) {
                server = "diasmp";
            }
            return statusCache.display(server);
        }
        return null;
    }
}

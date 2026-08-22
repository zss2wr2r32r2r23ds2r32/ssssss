package dev.sharded.velocitycore.util;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ServerResolver {

    private ServerResolver() {
    }

    public static Optional<RegisteredServer> find(ProxyServer server, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        Optional<RegisteredServer> direct = server.getServer(name);
        if (direct.isPresent()) {
            return direct;
        }

        String normalized = name.toLowerCase(Locale.ROOT);
        return server.getAllServers().stream()
                .filter(registered -> registered.getServerInfo().getName().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    public static String canonicalName(ProxyServer server, String name) {
        return find(server, name)
                .map(registered -> registered.getServerInfo().getName())
                .orElse(name.toLowerCase(Locale.ROOT));
    }

    public static String availableServers(ProxyServer server) {
        return server.getAllServers().stream()
                .map(registered -> registered.getServerInfo().getName())
                .collect(Collectors.joining(", "));
    }
}

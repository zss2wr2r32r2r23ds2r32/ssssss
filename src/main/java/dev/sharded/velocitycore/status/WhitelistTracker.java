package dev.sharded.velocitycore.status;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class WhitelistTracker {

    private final Set<String> whitelisted = ConcurrentHashMap.newKeySet();

    public void mark(String serverName) {
        whitelisted.add(normalize(serverName));
    }

    public void clear(String serverName) {
        whitelisted.remove(normalize(serverName));
    }

    public boolean isWhitelisted(String serverName) {
        return whitelisted.contains(normalize(serverName));
    }

    private static String normalize(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }
}

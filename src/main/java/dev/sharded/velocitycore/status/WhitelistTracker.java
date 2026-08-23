package dev.sharded.velocitycore.status;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WhitelistTracker {

    private static final long REPORT_TTL_MS = 120_000L;

    private final Map<String, Boolean> states = new ConcurrentHashMap<>();
    private final Map<String, Long> lastReportMs = new ConcurrentHashMap<>();

    public void setWhitelisted(String serverName, boolean enabled) {
        String key = normalize(serverName);
        states.put(key, enabled);
        lastReportMs.put(key, System.currentTimeMillis());
    }

    public boolean isWhitelisted(String serverName) {
        if (!hasRecentReport(serverName)) {
            return false;
        }
        return Boolean.TRUE.equals(states.get(normalize(serverName)));
    }

    public boolean hasRecentReport(String serverName) {
        Long lastReport = lastReportMs.get(normalize(serverName));
        return lastReport != null && System.currentTimeMillis() - lastReport <= REPORT_TTL_MS;
    }

    private static String normalize(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }
}

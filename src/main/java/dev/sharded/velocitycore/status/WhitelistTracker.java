package dev.sharded.velocitycore.status;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WhitelistTracker {

    private static final long REPORT_TTL_MS = 30_000L;

    private final Map<String, Boolean> whitelisted = new ConcurrentHashMap<>();
    private final Map<String, Long> lastReportMs = new ConcurrentHashMap<>();

    public void setWhitelisted(String serverName, boolean enabled) {
        String key = normalize(serverName);
        if (enabled) {
            whitelisted.put(key, true);
        } else {
            whitelisted.remove(key);
        }
        lastReportMs.put(key, System.currentTimeMillis());
    }

    public void mark(String serverName) {
        setWhitelisted(serverName, true);
    }

    public void clear(String serverName) {
        setWhitelisted(serverName, false);
    }

    public boolean isWhitelisted(String serverName) {
        String key = normalize(serverName);
        Long lastReport = lastReportMs.get(key);
        if (lastReport == null || System.currentTimeMillis() - lastReport > REPORT_TTL_MS) {
            return false;
        }
        return whitelisted.containsKey(key);
    }

    public boolean hasRecentReport(String serverName) {
        Long lastReport = lastReportMs.get(normalize(serverName));
        return lastReport != null && System.currentTimeMillis() - lastReport <= REPORT_TTL_MS;
    }

    private static String normalize(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }
}

package dev.sharded.velocitycore.status;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class WhitelistTracker {

    private final Map<String, Boolean> states = new ConcurrentHashMap<>();
    private Consumer<Map<String, Boolean>> saveListener;

    public void setSaveListener(Consumer<Map<String, Boolean>> saveListener) {
        this.saveListener = saveListener;
    }

    public void load(Map<String, Boolean> saved) {
        states.clear();
        states.putAll(saved);
    }

    public void setWhitelisted(String serverName, boolean enabled) {
        String key = normalize(serverName);
        Boolean previous = states.get(key);
        states.put(key, enabled);
        if (previous == null || previous.booleanValue() != enabled) {
            persist();
        }
    }

    public boolean isWhitelisted(String serverName) {
        return Boolean.TRUE.equals(states.get(normalize(serverName)));
    }

    public boolean hasReport(String serverName) {
        return states.containsKey(normalize(serverName));
    }

    public Map<String, Boolean> snapshot() {
        return Map.copyOf(states);
    }

    private void persist() {
        Consumer<Map<String, Boolean>> listener = saveListener;
        if (listener != null) {
            listener.accept(snapshot());
        }
    }

    private static String normalize(String serverName) {
        return serverName.toLowerCase(Locale.ROOT);
    }
}

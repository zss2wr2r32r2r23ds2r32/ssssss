package dev.sharded.velocitycore.lobby.sync;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

public final class StatusCache {

    private volatile Snapshot snapshot = Snapshot.empty();

    public void update(Snapshot incoming) {
        this.snapshot = incoming;
    }

    public String display(String serverName) {
        return snapshot.display(serverName);
    }

    public static final class Snapshot {
        private final Map<String, Entry> entries;

        Snapshot(Map<String, Entry> entries) {
            this.entries = entries;
        }

        static Snapshot empty() {
            return new Snapshot(Map.of());
        }

        String display(String serverName) {
            Entry entry = entries.get(serverName.toLowerCase(Locale.ROOT));
            return entry == null ? "&#FF0000&lOFFLINE" : entry.display();
        }
    }

    public record Entry(String display) {
    }
}

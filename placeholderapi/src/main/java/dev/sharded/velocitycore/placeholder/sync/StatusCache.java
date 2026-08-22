package dev.sharded.velocitycore.placeholder.sync;

import dev.sharded.velocitycore.common.ServerState;

import java.util.Collections;
import java.util.HashMap;
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

    public ServerState state(String serverName) {
        return snapshot.state(serverName);
    }

    public static Snapshot.Builder snapshotBuilder() {
        return new Snapshot.Builder();
    }

    public static final class Snapshot {
        private final Map<String, Entry> entries;

        private Snapshot(Map<String, Entry> entries) {
            this.entries = entries;
        }

        static Snapshot empty() {
            return new Snapshot(Map.of());
        }

        String display(String serverName) {
            Entry entry = entries.get(serverName.toLowerCase(Locale.ROOT));
            return entry == null ? ServerState.OFFLINE.display() : entry.display();
        }

        ServerState state(String serverName) {
            Entry entry = entries.get(serverName.toLowerCase(Locale.ROOT));
            return entry == null ? ServerState.OFFLINE : entry.state();
        }

        Map<String, Entry> entries() {
            return Collections.unmodifiableMap(entries);
        }

        public static final class Builder {
            private final Map<String, Entry> entries = new HashMap<>();

            public Builder put(String server, ServerState state, String display) {
                entries.put(server.toLowerCase(Locale.ROOT), new Entry(state, display));
                return this;
            }

            public Snapshot build() {
                return new Snapshot(Map.copyOf(entries));
            }
        }

        record Entry(ServerState state, String display) {
        }
    }
}

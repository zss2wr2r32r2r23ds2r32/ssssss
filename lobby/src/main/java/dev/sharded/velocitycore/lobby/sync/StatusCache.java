package dev.sharded.velocitycore.lobby.sync;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class StatusCache {

    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Snapshot snapshot = Snapshot.empty();

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public boolean update(Snapshot incoming) {
        if (snapshot.equals(incoming)) {
            return false;
        }
        this.snapshot = incoming;
        listeners.forEach(Runnable::run);
        return true;
    }

    public String display(String serverName) {
        return snapshot.display(serverName);
    }

    public static final class Snapshot {
        private final Map<String, Entry> entries;

        Snapshot(Map<String, Entry> entries) {
            this.entries = Map.copyOf(entries);
        }

        static Snapshot empty() {
            return new Snapshot(Map.of());
        }

        String display(String serverName) {
            Entry entry = entries.get(serverName.toLowerCase(Locale.ROOT));
            return entry == null ? "&#FF0000&lOFFLINE" : entry.display();
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Snapshot that)) {
                return false;
            }
            return entries.equals(that.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(entries);
        }
    }

    public record Entry(String display) {
    }
}

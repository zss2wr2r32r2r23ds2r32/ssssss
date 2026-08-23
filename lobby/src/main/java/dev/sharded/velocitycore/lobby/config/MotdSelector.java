package dev.sharded.velocitycore.lobby.config;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class MotdSelector {

    private int listIndex;
    private int lastRandomIndex = -1;

    public List<String> selectLines(String order, List<MotdConfig.MotdEntry> entries, List<String> fallback) {
        if (entries == null || entries.isEmpty()) {
            return fallback;
        }
        if (entries.size() == 1) {
            return entries.getFirst().lines();
        }

        int index = switch (order == null ? "list" : order.toLowerCase()) {
            case "random" -> pickRandom(entries.size());
            default -> pickList(entries.size());
        };
        return entries.get(index).lines();
    }

    public MotdConfig.MotdEntry selectEntry(String order, List<MotdConfig.MotdEntry> entries, MotdConfig.MotdEntry fallback) {
        if (entries == null || entries.isEmpty()) {
            return fallback;
        }
        if (entries.size() == 1) {
            return entries.getFirst();
        }
        int index = switch (order == null ? "list" : order.toLowerCase()) {
            case "random" -> pickRandom(entries.size());
            default -> pickList(entries.size());
        };
        return entries.get(index);
    }

    private int pickList(int size) {
        int index = listIndex % size;
        listIndex = (listIndex + 1) % size;
        return index;
    }

    private int pickRandom(int size) {
        if (size <= 1) {
            return 0;
        }
        int index = ThreadLocalRandom.current().nextInt(size);
        if (index == lastRandomIndex) {
            index = (index + 1) % size;
        }
        lastRandomIndex = index;
        return index;
    }
}

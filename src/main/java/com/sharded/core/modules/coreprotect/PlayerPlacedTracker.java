package com.sharded.core.modules.coreprotect;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Tracks player-placed blocks in side PvP regions. */
final class PlayerPlacedTracker {

    private final File file;
    private final Set<String> blocks = new HashSet<>();

    PlayerPlacedTracker(File moduleFolder) {
        this.file = new File(moduleFolder, "player-placed.yml");
        load();
    }

    void mark(Location location) {
        blocks.add(key(location));
    }

    void unmark(Location location) {
        blocks.remove(key(location));
    }

    boolean isPlaced(Location location) {
        return blocks.contains(key(location));
    }

    void clearSide(String sideId) {
        String prefix = sideId.toLowerCase() + ":";
        blocks.removeIf(key -> key.startsWith(prefix));
    }

    void clearAll() {
        blocks.clear();
    }

    void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", List.copyOf(blocks));
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        blocks.clear();
        for (String entry : yaml.getStringList("blocks")) {
            if (entry != null && !entry.isBlank()) blocks.add(entry);
        }
    }

    static String key(Location location) {
        if (location.getWorld() == null) return "?";
        return location.getWorld().getName().toLowerCase() + ":"
                + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }
}

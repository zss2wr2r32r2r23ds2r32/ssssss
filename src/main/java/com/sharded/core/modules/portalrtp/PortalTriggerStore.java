package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Stores portal block locations where the RTP GUI should open (set via portal wand). */
public final class PortalTriggerStore {

    private final ShardedCore plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Set<String> triggers = new HashSet<>();

    public PortalTriggerStore(ShardedCore plugin, File folder) {
        this.plugin = plugin;
        this.file = new File(folder, "portal-triggers.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        reload();
    }

    public void reload() {
        triggers.clear();
        List<String> list = yaml.getStringList("triggers");
        if (list != null) triggers.addAll(list);
    }

    public boolean isTrigger(Location location) {
        if (location.getWorld() == null) return false;
        if (triggers.isEmpty()) return true; // no wand points = any portal in world
        return triggers.contains(key(location));
    }

    public void add(Location location) {
        triggers.add(key(location));
        yaml.set("triggers", List.copyOf(triggers));
        save();
    }

    public void remove(Location location) {
        triggers.remove(key(location));
        yaml.set("triggers", List.copyOf(triggers));
        save();
    }

    public int count() {
        return triggers.size();
    }

    private String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save portal-triggers.yml: " + e.getMessage());
        }
    }
}

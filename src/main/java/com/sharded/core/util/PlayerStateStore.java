package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Simple persistent per-player key/value store (players.yml) shared between
 * modules: chat toggle, pm toggle, night vision, hide state, cooldowns, ...
 */
public final class PlayerStateStore {

    private final ShardedCore plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private boolean dirty;

    public PlayerStateStore(ShardedCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        // Autosave every 5 minutes if something changed.
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::saveIfDirty, 6000L, 6000L);
    }

    public boolean getBool(UUID uuid, String key, boolean def) {
        return yaml.getBoolean(uuid + "." + key, def);
    }

    public void setBool(UUID uuid, String key, boolean value) {
        yaml.set(uuid + "." + key, value);
        dirty = true;
    }

    public long getLong(UUID uuid, String key, long def) {
        return yaml.getLong(uuid + "." + key, def);
    }

    public void setLong(UUID uuid, String key, long value) {
        yaml.set(uuid + "." + key, value);
        dirty = true;
    }

    public String getString(UUID uuid, String key, String def) {
        return yaml.getString(uuid + "." + key, def);
    }

    public void setString(UUID uuid, String key, String value) {
        yaml.set(uuid + "." + key, value);
        dirty = true;
    }

    private synchronized void saveIfDirty() {
        if (dirty) saveNow();
    }

    public synchronized void saveNow() {
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save players.yml: " + e.getMessage());
        }
    }
}

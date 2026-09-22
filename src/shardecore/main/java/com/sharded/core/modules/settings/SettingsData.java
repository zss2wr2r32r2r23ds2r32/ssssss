package com.sharded.core.modules.settings;

import com.sharded.core.ShardedCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class SettingsData {

    private final ShardedCore plugin;
    private final File file;
    private FileConfiguration data;
    private final Map<UUID, Map<String, Boolean>> toggles = new HashMap<>();

    public SettingsData(ShardedCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "modules/settings/data.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create settings data.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        toggles.clear();
        if (data.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            Map<String, Boolean> map = new HashMap<>();
            if (data.getConfigurationSection("players." + key) != null) {
                for (String toggle : data.getConfigurationSection("players." + key).getKeys(false)) {
                    map.put(toggle, data.getBoolean("players." + key + "." + toggle));
                }
            }
            toggles.put(uuid, map);
        }
    }

    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }
        data.set("players", null);
        for (Map.Entry<UUID, Map<String, Boolean>> entry : toggles.entrySet()) {
            for (Map.Entry<String, Boolean> toggle : entry.getValue().entrySet()) {
                data.set("players." + entry.getKey() + "." + toggle.getKey(), toggle.getValue());
            }
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving settings data", e);
        }
    }

    public boolean get(UUID uuid, String key, boolean def) {
        return toggles.getOrDefault(uuid, Map.of()).getOrDefault(key, def);
    }

    public void set(UUID uuid, String key, boolean value) {
        toggles.computeIfAbsent(uuid, ignored -> new HashMap<>()).put(key, value);
        save();
    }

    public void toggle(UUID uuid, String key, boolean def) {
        set(uuid, key, !get(uuid, key, def));
    }
}

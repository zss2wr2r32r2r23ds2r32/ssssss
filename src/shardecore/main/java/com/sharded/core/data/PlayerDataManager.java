package com.sharded.core.data;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerDataManager {
    private final ShardedCore plugin;
    private final File file;
    private FileConfiguration data;
    private final Map<UUID, Double> money = new HashMap<>();
    private final Map<UUID, Long> shards = new HashMap<>();

    public PlayerDataManager(ShardedCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void load() {
        if (!this.file.exists()) {
            try {
                this.file.getParentFile().mkdirs();
                this.file.createNewFile();
            } catch (Exception e) {
                this.plugin.getLogger().warning("Could not create playerdata.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(this.file);
        this.money.clear();
        this.shards.clear();
        if (this.data.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : this.data.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            this.money.put(uuid, this.data.getDouble("players." + key + ".money", 0D));
            this.shards.put(uuid, this.data.getLong("players." + key + ".shards", 0L));
        }
    }

    public void save() {
        if (this.data == null) {
            this.data = new YamlConfiguration();
        }
        this.data.set("players", null);
        Set<UUID> all = new HashSet<>();
        all.addAll(this.money.keySet());
        all.addAll(this.shards.keySet());
        for (UUID uuid : all) {
            String path = "players." + uuid;
            this.data.set(path + ".money", this.money.getOrDefault(uuid, 0D));
            this.data.set(path + ".shards", this.shards.getOrDefault(uuid, 0L));
        }
        try {
            this.data.save(this.file);
        } catch (Exception e) {
            this.plugin.getLogger().warning("Failed saving playerdata.yml: " + e.getMessage());
        }
    }

    public double getMoney(UUID uuid) {
        return this.money.getOrDefault(uuid, 0D);
    }

    public long getShards(UUID uuid) {
        return this.shards.getOrDefault(uuid, 0L);
    }

    public Set<UUID> getKnownPlayers() {
        Set<UUID> all = new HashSet<>();
        all.addAll(this.money.keySet());
        all.addAll(this.shards.keySet());
        return all;
    }
}

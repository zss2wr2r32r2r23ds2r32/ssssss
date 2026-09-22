package com.sharded.core.modules.stats;

import com.sharded.core.ShardedCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public final class StatsData {

    private final ShardedCore plugin;
    private final File file;
    private FileConfiguration data;
    private final Map<UUID, PlayerStats> stats = new HashMap<>();

    public StatsData(ShardedCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "modules/stats/data.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create stats data.yml", e);
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        stats.clear();
        if (data.getConfigurationSection("players") == null) {
            return;
        }
        for (String key : data.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            String path = "players." + key + ".";
            stats.put(uuid, new PlayerStats(
                    data.getLong(path + "first-join", 0L),
                    data.getInt(path + "joins", 0),
                    data.getInt(path + "kills", 0),
                    data.getInt(path + "deaths", 0),
                    data.getLong(path + "playtime-millis", 0L),
                    data.getInt(path + "totems", 0),
                    data.getString(path + "name", key)
            ));
        }
    }

    public void save() {
        if (data == null) {
            data = new YamlConfiguration();
        }
        data.set("players", null);
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            String path = "players." + entry.getKey();
            PlayerStats s = entry.getValue();
            data.set(path + ".first-join", s.firstJoin);
            data.set(path + ".joins", s.joins);
            data.set(path + ".kills", s.kills);
            data.set(path + ".deaths", s.deaths);
            data.set(path + ".playtime-millis", s.playtimeMillis);
            data.set(path + ".totems", s.totems);
            data.set(path + ".name", s.name);
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed saving stats data", e);
        }
    }

    public PlayerStats get(UUID uuid) {
        return stats.computeIfAbsent(uuid, ignored -> new PlayerStats(0L, 0, 0, 0, 0L, 0, ""));
    }

    public Map<UUID, PlayerStats> getAll() {
        return java.util.Collections.unmodifiableMap(stats);
    }

    public UUID findByName(String name) {
        for (Map.Entry<UUID, PlayerStats> entry : stats.entrySet()) {
            if (entry.getValue().name != null && entry.getValue().name.equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public java.util.Collection<String> knownNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (PlayerStats s : stats.values()) {
            if (s.name != null && !s.name.isBlank()) {
                names.add(s.name);
            }
        }
        return names;
    }

    public static final class PlayerStats {
        public long firstJoin;
        public int joins;
        public int kills;
        public int deaths;
        public long playtimeMillis;
        public int totems;
        public String name;

        public PlayerStats(long firstJoin, int joins, int kills, int deaths, long playtimeMillis, int totems, String name) {
            this.firstJoin = firstJoin;
            this.joins = joins;
            this.kills = kills;
            this.deaths = deaths;
            this.playtimeMillis = playtimeMillis;
            this.totems = totems;
            this.name = name;
        }
    }
}

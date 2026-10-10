package com.shardedmc.lobbycore.manager;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class JoinCounterManager {

    private final ShardedLobbyCore plugin;
    private FileConfiguration data;

    public JoinCounterManager(ShardedLobbyCore plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "data.yml");
        if (!file.exists()) {
            plugin.saveResource("data.yml", false);
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public int nextJoinNumber() {
        int number = data.getInt("join-counter", 1);
        data.set("join-counter", number + 1);
        save();
        return number;
    }

    public int peekJoinNumber() {
        return data.getInt("join-counter", 1);
    }

    private void save() {
        try {
            data.save(new File(plugin.getDataFolder(), "data.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }
}

package com.shardedmc.lobbycore.manager;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PlaylistManager {

    private final ShardedLobbyCore plugin;
    private FileConfiguration data;

    public PlaylistManager(ShardedLobbyCore plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "playlists.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create playlists.yml");
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public List<String> getQueue(UUID uuid) {
        List<String> queue = data.getStringList("playlists." + uuid + ".queue");
        return new ArrayList<>(queue);
    }

    public void setQueue(UUID uuid, List<String> queue) {
        data.set("playlists." + uuid + ".queue", new ArrayList<>(queue));
        save();
    }

    public void toggleSong(UUID uuid, String songId) {
        List<String> queue = getQueue(uuid);
        if (queue.contains(songId)) {
            queue.remove(songId);
        } else {
            queue.add(songId);
        }
        setQueue(uuid, queue);
    }

    public boolean isSelected(UUID uuid, String songId) {
        return getQueue(uuid).contains(songId);
    }

    public String peekNext(UUID uuid) {
        List<String> queue = getQueue(uuid);
        return queue.isEmpty() ? null : queue.get(0);
    }

    public String pollNext(UUID uuid) {
        List<String> queue = getQueue(uuid);
        if (queue.isEmpty()) {
            return null;
        }
        String next = queue.remove(0);
        setQueue(uuid, queue);
        return next;
    }

    public void save() {
        try {
            data.save(new File(plugin.getDataFolder(), "playlists.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save playlists.yml: " + e.getMessage());
        }
    }
}

package com.shardedmc.lobbycore.manager;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlaylistManager {

    private final ShardedLobbyCore plugin;
    private FileConfiguration data;
    private final Map<UUID, List<String>> drafts = new HashMap<>();
    private final Set<UUID> playlistPlayback = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> manualSkipUntil = new ConcurrentHashMap<>();

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
        return new ArrayList<>(data.getStringList("playlists." + uuid + ".queue"));
    }

    public void setQueue(UUID uuid, List<String> queue) {
        data.set("playlists." + uuid + ".queue", new ArrayList<>(queue));
        save();
    }

    public List<String> getSavedPlaylist(UUID uuid) {
        return new ArrayList<>(data.getStringList("playlists." + uuid + ".songs"));
    }

    public void savePlaylist(UUID uuid, List<String> songs) {
        data.set("playlists." + uuid + ".songs", new ArrayList<>(songs));
        save();
    }

    public boolean hasPlaylist(UUID uuid) {
        return !getSavedPlaylist(uuid).isEmpty() || !getQueue(uuid).isEmpty();
    }

    public void beginPlaylistPlayback(UUID uuid) {
        playlistPlayback.add(uuid);
    }

    public void endPlaylistPlayback(UUID uuid) {
        playlistPlayback.remove(uuid);
        manualSkipUntil.remove(uuid);
    }

    public boolean isPlaylistPlayback(UUID uuid) {
        return playlistPlayback.contains(uuid);
    }

    public void markManualSkip(UUID uuid) {
        manualSkipUntil.put(uuid, System.currentTimeMillis() + 2000L);
    }

    public boolean isManualSkipCooldown(UUID uuid) {
        Long until = manualSkipUntil.get(uuid);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            manualSkipUntil.remove(uuid);
            return false;
        }
        return true;
    }

    public void startDraft(UUID uuid) {
        List<String> saved = getSavedPlaylist(uuid);
        drafts.put(uuid, saved.isEmpty() ? new ArrayList<>() : new ArrayList<>(saved));
    }

    public List<String> getDraft(UUID uuid) {
        return drafts.computeIfAbsent(uuid, k -> new ArrayList<>());
    }

    public void clearDraft(UUID uuid) {
        drafts.remove(uuid);
    }

    public void toggleDraftSong(UUID uuid, String songId) {
        List<String> draft = getDraft(uuid);
        if (draft.contains(songId)) {
            draft.remove(songId);
        } else {
            draft.add(songId);
        }
    }

    public boolean isInDraft(UUID uuid, String songId) {
        return getDraft(uuid).contains(songId);
    }

    public int getDraftPosition(UUID uuid, String songId) {
        int index = getDraft(uuid).indexOf(songId);
        return index >= 0 ? index + 1 : -1;
    }

    public List<String> confirmDraft(UUID uuid) {
        List<String> confirmed = new ArrayList<>(getDraft(uuid));
        drafts.remove(uuid);
        if (!confirmed.isEmpty()) {
            savePlaylist(uuid, confirmed);
        }
        return confirmed;
    }

    public String peekNext(UUID uuid) {
        List<String> queue = getQueue(uuid);
        if (!queue.isEmpty()) {
            return queue.get(0);
        }
        if (isPlaylistPlayback(uuid)) {
            return null;
        }
        List<String> saved = getSavedPlaylist(uuid);
        return saved.isEmpty() ? null : saved.get(0);
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

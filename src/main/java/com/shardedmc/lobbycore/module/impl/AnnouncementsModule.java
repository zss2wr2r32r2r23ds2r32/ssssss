package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class AnnouncementsModule implements Module {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private BukkitTask task;
    private int index;

    @Override
    public String getId() {
        return "announcements";
    }

    @Override
    public String getDisplayName() {
        return "Announcements";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.index = 0;

        long interval = config.getLong("interval-seconds", 120) * 20L;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNext, interval, interval);
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
        }
    }

    private void broadcastNext() {
        List<String> announcements = config.getStringList("messages");
        if (announcements.isEmpty()) {
            return;
        }

        if (index >= announcements.size()) {
            index = 0;
        }

        String message = announcements.get(index++);
        String prefix = config.getString("prefix", "&8[&bAnnouncement&8] &r");
        Bukkit.broadcast(MessageUtil.component(MessageUtil.colorize(prefix + message)));
    }
}

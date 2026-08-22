package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class VoidSpawnModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

    @Override
    public String getId() {
        return "void-spawn";
    }

    @Override
    public String getDisplayName() {
        return "Void Spawn";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        Player player = event.getPlayer();
        double voidY = config.getDouble("void-y", 0);

        if (player.getLocation().getY() <= voidY) {
            ParkourModule parkour = (ParkourModule) plugin.getModuleManager().getModule("parkour");
            if (parkour != null && parkour.isInParkour(player.getUniqueId())) {
                parkour.endParkourFromVoid(player);
            }
            player.teleport(plugin.getSpawnManager().getSpawn());
            if (config.getBoolean("reset-fall-damage", true)) {
                player.setFallDistance(0);
            }
        }
    }
}

package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class VoidSpawnModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private double voidY;
    private boolean resetFallDamage;

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
        this.voidY = config.getDouble("void-y", 0);
        this.resetFallDamage = config.getBoolean("reset-fall-damage", true);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        // Only care when Y block changes
        if (event.getFrom().getBlockY() == to.getBlockY()) {
            return;
        }
        if (to.getY() > voidY) {
            return;
        }

        Player player = event.getPlayer();
        ParkourModule parkour = (ParkourModule) plugin.getModuleManager().getModule("parkour");
        if (parkour != null && parkour.isInParkour(player.getUniqueId())) {
            parkour.endParkourFromVoid(player);
        }
        player.teleport(plugin.getSpawnManager().getSpawn());
        if (resetFallDamage) {
            player.setFallDistance(0);
        }
    }
}

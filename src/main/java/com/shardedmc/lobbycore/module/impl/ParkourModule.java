package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ParkourModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

    @Override
    public String getId() {
        return "parkour";
    }

    @Override
    public String getDisplayName() {
        return "Parkour";
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
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        Material blockMaterial = Material.matchMaterial(config.getString("block.material", "EMERALD_BLOCK"));
        if (blockMaterial == null || event.getClickedBlock().getType() != blockMaterial) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String command = config.getString("command", "ajparkour start").replace("%player%", player.getName());
        player.performCommand(command);
    }
}

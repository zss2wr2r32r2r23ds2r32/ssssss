package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.List;
import java.util.stream.Collectors;

public class ParkourModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private List<Material> blockMaterials;

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
        reloadMaterials();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void reloadMaterials() {
        blockMaterials = config.getStringList("blocks").stream()
                .map(Material::matchMaterial)
                .filter(m -> m != null)
                .collect(Collectors.toList());

        if (blockMaterials.isEmpty()) {
            Material material = Material.matchMaterial(config.getString("block.material", "LIME_GLAZED_TERRACOTTA"));
            if (material != null) {
                blockMaterials = List.of(material);
            }
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    public boolean isParkourBlock(Material material) {
        return blockMaterials.contains(material);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        if (event.getClickedBlock() == null) {
            return;
        }

        if (!blockMaterials.contains(event.getClickedBlock().getType())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String command = config.getString("command", "ajparkour start").replace("%player%", player.getName());
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }
}

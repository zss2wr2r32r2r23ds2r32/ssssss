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
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LaunchPadsModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private DoubleJumpModule doubleJumpModule;
    private final Set<UUID> cooldown = new HashSet<>();

    @Override
    public String getId() {
        return "launch-pads";
    }

    @Override
    public String getDisplayName() {
        return "Launch Pads";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        if (plugin.getModuleManager().getModule("double-jump") != null) {
            doubleJumpModule = (DoubleJumpModule) plugin.getModuleManager().getModule("double-jump");
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        cooldown.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }

        Player player = event.getPlayer();
        Material below = player.getLocation().subtract(0, 0.1, 0).getBlock().getType();
        List<String> plates = config.getStringList("materials");

        boolean isPlate = plates.stream()
                .map(Material::matchMaterial)
                .anyMatch(m -> m != null && m == below);

        if (!isPlate && config.getBoolean("any-pressure-plate", true)) {
            isPlate = below.name().endsWith("_PRESSURE_PLATE");
        }

        if (!isPlate) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (cooldown.contains(uuid)) {
            return;
        }
        cooldown.add(uuid);

        if (doubleJumpModule != null) {
            doubleJumpModule.performLaunch(player);
        } else {
            org.bukkit.util.Vector direction = player.getLocation().getDirection().normalize()
                    .multiply(config.getDouble("power", 1.2));
            direction.setY(config.getDouble("vertical-boost", 0.8));
            player.setVelocity(direction);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> cooldown.remove(uuid), config.getLong("cooldown-ticks", 10));
    }
}

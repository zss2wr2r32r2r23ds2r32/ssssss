package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class LaunchPadsModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private final Set<UUID> cooldown = new HashSet<>();
    private Set<Material> plateMaterials;
    private boolean anyPressurePlate;
    private double power;
    private double verticalBoost;
    private long cooldownTicks;
    private boolean playSound;
    private Sound sound;
    private float soundVolume;
    private float soundPitch;

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
        reloadSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void reloadSettings() {
        power = config.getDouble("power", 1.5);
        verticalBoost = config.getDouble("vertical-boost", 1.0);
        cooldownTicks = config.getLong("cooldown-ticks", 10);
        anyPressurePlate = config.getBoolean("any-pressure-plate", true);
        playSound = config.getBoolean("play-sound", true);
        soundVolume = (float) config.getDouble("sound.volume", 1.0);
        soundPitch = (float) config.getDouble("sound.pitch", 1.2);

        String soundName = config.getString("sound.name", "ENTITY_BAT_TAKEOFF");
        try {
            sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (IllegalArgumentException ex) {
            sound = Sound.ENTITY_BAT_TAKEOFF;
        }

        plateMaterials = new HashSet<>();
        List<String> plates = config.getStringList("materials");
        for (String name : plates) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                plateMaterials.add(material);
            }
        }
    }

    @Override
    public void disable() {
        cooldown.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        // Only check when the player enters a new block — skips tiny head movements
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (cooldown.contains(uuid)) {
            return;
        }

        ParkourModule parkour = (ParkourModule) plugin.getModuleManager().getModule("parkour");
        if (parkour != null && parkour.isInParkour(uuid)) {
            return;
        }

        if (!isOnPressurePlate(to)) {
            return;
        }

        cooldown.add(uuid);
        launch(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> cooldown.remove(uuid), cooldownTicks);
    }

    private void launch(Player player) {
        Vector direction = player.getLocation().getDirection().normalize().multiply(power);
        direction.setY(verticalBoost);
        player.setVelocity(direction);

        if (playSound) {
            player.playSound(player.getLocation(), sound, soundVolume, soundPitch);
        }
    }

    private boolean isOnPressurePlate(Location location) {
        Block feet = location.getBlock();
        if (isPressurePlate(feet.getType())) {
            return true;
        }
        Block below = feet.getRelative(BlockFace.DOWN);
        return isPressurePlate(below.getType());
    }

    private boolean isPressurePlate(Material material) {
        if (material == null || material.isAir()) {
            return false;
        }
        if (plateMaterials.contains(material)) {
            return true;
        }
        return anyPressurePlate && material.name().endsWith("_PRESSURE_PLATE");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldown.remove(event.getPlayer().getUniqueId());
    }
}

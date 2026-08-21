package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DoubleJumpModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private final Set<UUID> hasJumped = new HashSet<>();

    @Override
    public String getId() {
        return "double-jump";
    }

    @Override
    public String getDisplayName() {
        return "Double Jump";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (isAdminFlying(player)) {
                    continue;
                }
                if (player.isOnGround() || player.isInWater() || player.isClimbing()) {
                    hasJumped.remove(player.getUniqueId());
                    if (!player.getAllowFlight()) {
                        player.setAllowFlight(true);
                    }
                }
            }
        }, 0L, 5L);
    }

    @Override
    public void disable() {
        hasJumped.clear();
        HandlerList.unregisterAll(this);
    }

    private boolean isAdminFlying(Player player) {
        JoinMessagesModule joinMessages = (JoinMessagesModule) plugin.getModuleManager().getModule("join-messages");
        return joinMessages != null && joinMessages.isAdminFlying(player);
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (isAdminFlying(player)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        if (hasJumped.contains(uuid)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        player.setAllowFlight(false);
        hasJumped.add(uuid);

        double power = config.getDouble("power", 1.2);
        double vertical = config.getDouble("vertical-boost", 0.8);
        Vector direction = player.getLocation().getDirection().normalize().multiply(power);
        direction.setY(vertical);
        player.setVelocity(direction);

        if (config.getBoolean("play-sound", true)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1f, 1.2f);
        }

        if (!config.getString("messages.used", "").isEmpty()) {
            MessageUtil.sendFormatted(player, config.getString("messages.used"));
        }
    }

    public void performLaunch(Player player) {
        double power = config.getDouble("power", 1.2);
        double vertical = config.getDouble("vertical-boost", 0.8);
        Vector direction = player.getLocation().getDirection().normalize().multiply(power);
        direction.setY(vertical);
        player.setVelocity(direction);

        if (config.getBoolean("play-sound", true)) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1f, 1.2f);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isOnGround()) {
            hasJumped.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hasJumped.remove(event.getPlayer().getUniqueId());
    }
}

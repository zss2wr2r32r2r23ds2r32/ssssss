package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.meta.FireworkMeta;

import java.util.concurrent.ThreadLocalRandom;

public class JoinActionsModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

    @Override
    public String getId() {
        return "join-actions";
    }

    @Override
    public String getDisplayName() {
        return "Join Actions";
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
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (config.getBoolean("teleport-to-spawn", true)) {
                player.teleport(plugin.getSpawnManager().getSpawn());
            }

            if (config.getBoolean("fireworks.enabled", true)) {
                spawnFireworks(player);
            }
        }, config.getLong("delay-ticks", 5));
    }

    private void spawnFireworks(Player player) {
        int amount = config.getInt("fireworks.amount", 2);
        for (int i = 0; i < amount; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Firework firework = (Firework) player.getWorld().spawnEntity(player.getLocation(), EntityType.FIREWORK);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(randomColor(), randomColor())
                        .flicker(true)
                        .trail(true)
                        .build());
                meta.setPower(1);
                firework.setFireworkMeta(meta);
            }, i * 5L);
        }
    }

    private Color randomColor() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return Color.fromRGB(random.nextInt(256), random.nextInt(256), random.nextInt(256));
    }
}

package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

public class WorldProtectionModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

    @Override
    public String getId() {
        return "world-protection";
    }

    @Override
    public String getDisplayName() {
        return "World Protection";
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (config.getBoolean("disable-damage", true) && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (config.getBoolean("disable-hunger", true) && event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (config.getBoolean("disable-block-break", true) && !event.getPlayer().hasPermission("shardedlobbycore.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (config.getBoolean("disable-block-place", true) && !event.getPlayer().hasPermission("shardedlobbycore.admin")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (config.getBoolean("disable-item-drop", true) && event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (config.getBoolean("disable-inventory-move", false) && event.getWhoClicked() instanceof Player player) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent event) {
        if (config.getBoolean("disable-weather", true) && event.toWeatherState()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (config.getBoolean("disable-block-interact", true) &&
                event.getClickedBlock() != null &&
                !event.getPlayer().hasPermission("shardedlobbycore.admin")) {
            switch (event.getClickedBlock().getType()) {
                case CHEST, TRAPPED_CHEST, FURNACE, BLAST_FURNACE, SMOKER, BARREL, HOPPER, DROPPER, DISPENSER, LEVER, STONE_BUTTON, OAK_BUTTON -> event.setCancelled(true);
                default -> {
                    if (event.getClickedBlock().getType().name().endsWith("_DOOR") ||
                            event.getClickedBlock().getType().name().endsWith("_TRAPDOOR") ||
                            event.getClickedBlock().getType().name().endsWith("_FENCE_GATE")) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBurn(BlockBurnEvent event) {
        if (config.getBoolean("disable-fire-spread", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent event) {
        if (config.getBoolean("disable-fire-spread", true)) {
            event.setCancelled(true);
        }
    }
}

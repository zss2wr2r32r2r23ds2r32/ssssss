package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.gui.MenuHolder;
import com.shardedmc.lobbycore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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

    private boolean isInPvp(Player player) {
        PvpModule pvp = (PvpModule) plugin.getModuleManager().getModule("pvp");
        return pvp != null && pvp.isInPvp(player.getUniqueId());
    }

    private boolean canBypassInventory(Player player) {
        return player.getGameMode() == GameMode.CREATIVE;
    }

    private boolean canBypassBuild(Player player) {
        return player.hasPermission("shardedlobbycore.admin") || player.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (!config.getBoolean("disable-damage", true) || !(event.getEntity() instanceof Player victim)) {
            return;
        }

        if (event instanceof EntityDamageByEntityEvent damageByEntity) {
            Player attacker = resolveAttacker(damageByEntity);
            if (attacker != null && isInPvp(victim) && isInPvp(attacker)) {
                return;
            }
        }

        event.setCancelled(true);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile &&
                projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (config.getBoolean("disable-hunger", true) && event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onBreak(org.bukkit.event.block.BlockBreakEvent event) {
        if (config.getBoolean("disable-block-break", true) && !canBypassBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (config.getBoolean("disable-block-place", true) && !canBypassBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (config.getBoolean("disable-portals", true) && !canBypassBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!config.getBoolean("disable-item-drop", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (canBypassInventory(player)) {
            return;
        }
        if (isInPvp(player) && config.getBoolean("allow-drop-in-pvp", false)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.getBoolean("disable-inventory-move", true) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
            return;
        }
        if (canBypassInventory(player)) {
            return;
        }
        if (isInPvp(player) && config.getBoolean("allow-inventory-move-in-pvp", true)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!config.getBoolean("disable-inventory-move", true) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (canBypassInventory(player)) {
            return;
        }
        if (isInPvp(player) && config.getBoolean("allow-inventory-move-in-pvp", true)) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (!config.getBoolean("disable-inventory-move", true)) {
            return;
        }
        Player player = event.getPlayer();
        if (canBypassInventory(player)) {
            return;
        }
        if (isInPvp(player) && config.getBoolean("allow-inventory-move-in-pvp", true)) {
            return;
        }
        event.setCancelled(true);
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
                !canBypassBuild(event.getPlayer())) {
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
    public void onBurn(org.bukkit.event.block.BlockBurnEvent event) {
        if (config.getBoolean("disable-fire-spread", true)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        if (config.getBoolean("disable-fire-spread", true)) {
            event.setCancelled(true);
        }
    }
}

package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BowPopperModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private NamespacedKey arrowKey;
    private NamespacedKey launchXKey;
    private NamespacedKey launchYKey;
    private NamespacedKey launchZKey;
    private final Map<UUID, Location> arrowLaunchLocations = new HashMap<>();

    @Override
    public String getId() {
        return "bow-popper";
    }

    @Override
    public String getDisplayName() {
        return "Bow Popper";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.arrowKey = new NamespacedKey(plugin, "bow-popper-arrow");
        this.launchXKey = new NamespacedKey(plugin, "bow-popper-x");
        this.launchYKey = new NamespacedKey(plugin, "bow-popper-y");
        this.launchZKey = new NamespacedKey(plugin, "bow-popper-z");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        arrowLaunchLocations.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player player)) {
            return;
        }

        PvpModule pvpModule = (PvpModule) plugin.getModuleManager().getModule("pvp");
        if (pvpModule != null && pvpModule.isInPvp(player.getUniqueId())) {
            return;
        }

        if (!isBowPopperBow(player)) {
            return;
        }

        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), "bow-popper")) {
            event.setCancelled(true);
            long remaining = plugin.getCooldownManager().getRemainingSeconds(player.getUniqueId(), "bow-popper");
            MessageUtil.sendFormatted(player, config.getString("messages.cooldown", "%prefix% &#FF2727Wait %seconds%s before using Bow Popper again.")
                    .replace("%seconds%", String.valueOf(remaining)));
            return;
        }

        Location launch = player.getLocation().clone();
        arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
        arrow.getPersistentDataContainer().set(launchXKey, PersistentDataType.DOUBLE, launch.getX());
        arrow.getPersistentDataContainer().set(launchYKey, PersistentDataType.DOUBLE, launch.getY());
        arrow.getPersistentDataContainer().set(launchZKey, PersistentDataType.DOUBLE, launch.getZ());
        arrowLaunchLocations.put(arrow.getUniqueId(), launch);
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
    }

    @EventHandler
    public void onHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (!(arrow.getShooter() instanceof Player player)) {
            return;
        }
        if (!arrow.getPersistentDataContainer().has(arrowKey, PersistentDataType.BYTE)) {
            return;
        }

        Location launch = getLaunchLocation(arrow);
        arrowLaunchLocations.remove(arrow.getUniqueId());
        double maxRange = config.getDouble("max-range", 100);

        if (launch != null && arrow.getLocation().distance(launch) > maxRange) {
            arrow.remove();
            MessageUtil.sendFormatted(player, config.getString("messages.out-of-range", "%prefix% &#FF2727Your arrow went too far! Max range is %range% blocks.")
                    .replace("%range%", String.valueOf((int) maxRange)));
            return;
        }

        long cooldown = config.getLong("cooldown-seconds", 5);
        plugin.getCooldownManager().setCooldown(player.getUniqueId(), "bow-popper", cooldown);

        Location target = arrow.getLocation().clone();
        target.setYaw(player.getLocation().getYaw());
        target.setPitch(player.getLocation().getPitch());

        arrow.remove();
        Bukkit.getScheduler().runTask(plugin, () -> player.teleport(target));

        if (!config.getString("messages.teleported", "").isEmpty()) {
            MessageUtil.sendFormatted(player, config.getString("messages.teleported", "%prefix% &#9FFF00Teleported to your arrow!"));
        }
    }

    private Location getLaunchLocation(Arrow arrow) {
        Location cached = arrowLaunchLocations.get(arrow.getUniqueId());
        if (cached != null) {
            return cached;
        }
        if (!arrow.getPersistentDataContainer().has(launchXKey, PersistentDataType.DOUBLE)) {
            return null;
        }
        return new Location(
                arrow.getWorld(),
                arrow.getPersistentDataContainer().get(launchXKey, PersistentDataType.DOUBLE),
                arrow.getPersistentDataContainer().get(launchYKey, PersistentDataType.DOUBLE),
                arrow.getPersistentDataContainer().get(launchZKey, PersistentDataType.DOUBLE)
        );
    }

    private boolean isBowPopperBow(Player player) {
        return isBowPopperItem(player.getInventory().getItemInMainHand()) ||
                isBowPopperItem(player.getInventory().getItemInOffHand());
    }

    private boolean isBowPopperItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        if (config.isConfigurationSection("item")) {
            Material material = Material.matchMaterial(config.getString("item.material", "BOW"));
            if (ItemBuilder.matchesMaterial(item, material) &&
                    ItemBuilder.matchesName(item, config.getString("item.name", "&#FFE300Bow Popper"))) {
                return true;
            }
        }

        DefaultItemsModule defaultItems = (DefaultItemsModule) plugin.getModuleManager().getModule("default-items");
        if (defaultItems != null) {
            var section = defaultItems.getItemSection("bow-popper");
            if (section != null) {
                Material material = Material.matchMaterial(section.getString("material", "BOW"));
                return ItemBuilder.matchesMaterial(item, material) &&
                        ItemBuilder.matchesName(item, section.getString("name", "&#FFE300Bow Popper"));
            }
        }
        return false;
    }
}

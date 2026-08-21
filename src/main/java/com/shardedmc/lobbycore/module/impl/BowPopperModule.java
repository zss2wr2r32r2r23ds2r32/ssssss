package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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

public class BowPopperModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

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
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
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

        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!isBowPopper(bow)) {
            bow = player.getInventory().getItemInOffHand();
            if (!isBowPopper(bow)) {
                return;
            }
        }

        if (plugin.getCooldownManager().isOnCooldown(player.getUniqueId(), "bow-popper")) {
            event.setCancelled(true);
            long remaining = plugin.getCooldownManager().getRemainingSeconds(player.getUniqueId(), "bow-popper");
            MessageUtil.sendFormatted(player, config.getString("messages.cooldown", "&cWait %seconds%s before using Bow Popper again.")
                    .replace("%seconds%", String.valueOf(remaining)));
            return;
        }

        arrow.setShooter(player);
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

        ItemStack bow = player.getInventory().getItemInMainHand();
        if (!isBowPopper(bow)) {
            bow = player.getInventory().getItemInOffHand();
            if (!isBowPopper(bow)) {
                return;
            }
        }

        long cooldown = config.getLong("cooldown-seconds", 5);
        plugin.getCooldownManager().setCooldown(player.getUniqueId(), "bow-popper", cooldown);

        arrow.remove();
        player.teleport(arrow.getLocation());
        MessageUtil.sendFormatted(player, config.getString("messages.teleported", "&aTeleported to your arrow!"));
    }

    private boolean isBowPopper(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material material = Material.matchMaterial(config.getString("item.material", "BOW"));
        return material != null && item.getType() == material &&
                ItemBuilder.matchesName(item, MessageUtil.colorize(config.getString("item.name", "&dBow Popper")));
    }
}

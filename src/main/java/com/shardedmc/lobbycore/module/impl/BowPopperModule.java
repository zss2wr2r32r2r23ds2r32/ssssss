package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
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

public class BowPopperModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private NamespacedKey arrowKey;

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
            MessageUtil.sendFormatted(player, config.getString("messages.cooldown", "&cWait %seconds%s before using Bow Popper again.")
                    .replace("%seconds%", String.valueOf(remaining)));
            return;
        }

        arrow.getPersistentDataContainer().set(arrowKey, PersistentDataType.BYTE, (byte) 1);
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

        long cooldown = config.getLong("cooldown-seconds", 5);
        plugin.getCooldownManager().setCooldown(player.getUniqueId(), "bow-popper", cooldown);

        arrow.remove();
        player.teleport(arrow.getLocation());
        MessageUtil.sendFormatted(player, config.getString("messages.teleported", "&aTeleported to your arrow!"));
    }

    private boolean isBowPopperBow(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        if (isBowPopperItem(main)) {
            return true;
        }
        return isBowPopperItem(off);
    }

    private boolean isBowPopperItem(ItemStack item) {
        if (item == null) {
            return false;
        }

        Material configMaterial = Material.matchMaterial(config.getString("item.material", "BOW"));
        if (ItemBuilder.matchesMaterial(item, configMaterial) &&
                ItemBuilder.matchesName(item, config.getString("item.name", "&d&lBOW POPPER"))) {
            return true;
        }

        DefaultItemsModule defaultItems = (DefaultItemsModule) plugin.getModuleManager().getModule("default-items");
        if (defaultItems != null) {
            var section = defaultItems.getItemSection("bow-popper");
            if (section != null) {
                Material material = Material.matchMaterial(section.getString("material", "BOW"));
                return ItemBuilder.matchesMaterial(item, material) &&
                        ItemBuilder.matchesName(item, section.getString("name", "&d&lBOW POPPER"));
            }
        }
        return false;
    }
}

package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerVisibilityModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    @Override
    public String getId() {
        return "player-visibility";
    }

    @Override
    public String getDisplayName() {
        return "Player Visibility";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        for (UUID uuid : hiddenPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                showAllPlayers(player);
            }
        }
        hiddenPlayers.clear();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID uuid : hiddenPlayers) {
            Player hider = Bukkit.getPlayer(uuid);
            if (hider != null && !hider.equals(joined)) {
                hider.hidePlayer(plugin, joined);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        Material material = Material.matchMaterial(config.getString("item.material", "LIME_DYE"));
        String baseName = config.getString("item.name-enabled", "&aPLAYER VISIBILITY");
        if (material == null || item.getType() != material) {
            return;
        }

        if (!ItemBuilder.matchesName(item, MessageUtil.colorize(baseName)) &&
                !ItemBuilder.matchesName(item, MessageUtil.colorize(config.getString("item.name-disabled", "&cPLAYER VISIBILITY")))) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        toggleVisibility(player);
        updateItem(player);
    }

    private void toggleVisibility(Player player) {
        if (hiddenPlayers.contains(player.getUniqueId())) {
            hiddenPlayers.remove(player.getUniqueId());
            showAllPlayers(player);
            MessageUtil.sendFormatted(player, config.getString("messages.shown", "&aPlayers are now visible."));
        } else {
            hiddenPlayers.add(player.getUniqueId());
            hideAllPlayers(player);
            MessageUtil.sendFormatted(player, config.getString("messages.hidden", "&cPlayers are now hidden."));
        }
    }

    private void hideAllPlayers(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                player.hidePlayer(plugin, online);
            }
        }
    }

    private void showAllPlayers(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            player.showPlayer(plugin, online);
        }
    }

    public void updateItem(Player player) {
        int slot = config.getInt("item.slot", 8);
        boolean hidden = hiddenPlayers.contains(player.getUniqueId());
        Material material = Material.matchMaterial(hidden ?
                config.getString("item.material-disabled", "GRAY_DYE") :
                config.getString("item.material", "LIME_DYE"));
        if (material == null) {
            material = Material.LIME_DYE;
        }

        String status = hidden ? config.getString("status.disabled", "&7[DISABLED]") : config.getString("status.enabled", "&7[ENABLED]");
        String nameKey = hidden ? "item.name-disabled" : "item.name-enabled";
        String loreKey = hidden ? "item.lore-disabled" : "item.lore-enabled";

        ItemStack item = ItemBuilder.of(material)
                .name(MessageUtil.format(config.getString(nameKey, "&aPLAYER VISIBILITY") + " " + status, player))
                .lore(MessageUtil.formatLore(config.getStringList(loreKey), player))
                .build();

        player.getInventory().setItem(slot, item);
    }

    public boolean isHidden(UUID uuid) {
        return hiddenPlayers.contains(uuid);
    }
}

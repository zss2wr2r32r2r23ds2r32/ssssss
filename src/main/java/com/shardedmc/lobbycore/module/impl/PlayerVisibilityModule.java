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
    private Material enabledMaterial;
    private Material disabledMaterial;

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
        enabledMaterial = Material.matchMaterial(config.getString("item.material", "LIME_DYE"));
        disabledMaterial = Material.matchMaterial(config.getString("item.material-disabled", "RED_DYE"));
        if (enabledMaterial == null) {
            enabledMaterial = Material.LIME_DYE;
        }
        if (disabledMaterial == null) {
            disabledMaterial = Material.RED_DYE;
        }
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> updateItem(joined), 3L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        if (item == null || !isVisibilityItem(item)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (plugin.getCooldownManager().isOnCooldown(uuid, "player-visibility")) {
            long remaining = plugin.getCooldownManager().getRemainingSeconds(uuid, "player-visibility");
            MessageUtil.sendFormatted(player, config.getString("messages.cooldown", "%prefix% &cWait %seconds%s before toggling visibility again.")
                    .replace("%seconds%", String.valueOf(remaining)));
            return;
        }

        long cooldownSeconds = config.getLong("cooldown-seconds", 3);
        plugin.getCooldownManager().setCooldown(uuid, "player-visibility", cooldownSeconds);

        toggleVisibility(player);
        updateItem(player);
    }

    private boolean isVisibilityItem(ItemStack item) {
        return item.getType() == enabledMaterial || item.getType() == disabledMaterial;
    }

    private void toggleVisibility(Player player) {
        if (hiddenPlayers.contains(player.getUniqueId())) {
            hiddenPlayers.remove(player.getUniqueId());
            showAllPlayers(player);
            MessageUtil.sendFormatted(player, config.getString("messages.shown", "%prefix% &aAll players are now visible."));
        } else {
            hiddenPlayers.add(player.getUniqueId());
            hideAllPlayers(player);
            MessageUtil.sendFormatted(player, config.getString("messages.hidden", "%prefix% &cAll players are now hidden."));
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
        Material material = hidden ? disabledMaterial : enabledMaterial;

        String status = hidden ?
                config.getString("status.disabled", "&7[DISABLED]") :
                config.getString("status.enabled", "&7[ENABLED]");
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

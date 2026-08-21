package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class DefaultItemsModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private final Map<String, ConfigurationSection> items = new HashMap<>();

    @Override
    public String getId() {
        return "default-items";
    }

    @Override
    public String getDisplayName() {
        return "Default Items";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        items.clear();

        ConfigurationSection section = config.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                items.put(key, section.getConfigurationSection(key));
            }
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        items.clear();
    }

    public void giveItems(Player player) {
        if (!config.getBoolean("give-on-join", true)) {
            return;
        }

        for (Map.Entry<String, ConfigurationSection> entry : items.entrySet()) {
            ConfigurationSection itemSection = entry.getValue();
            int slot = itemSection.getInt("slot", 0);
            ItemStack item = ItemBuilder.fromConfig(itemSection, player);
            player.getInventory().setItem(slot, item);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            giveItems(event.getPlayer());
            PlayerVisibilityModule visibility = (PlayerVisibilityModule) plugin.getModuleManager().getModule("player-visibility");
            if (visibility != null) {
                visibility.updateItem(event.getPlayer());
            }
        }, 2L);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        if (hand == null) {
            return;
        }

        for (ConfigurationSection itemSection : items.values()) {
            Material material = Material.matchMaterial(itemSection.getString("material", "STONE"));
            String name = itemSection.getString("name");
            if (material != null && hand.getType() == material && ItemBuilder.matchesName(hand, MessageUtil.format(name, player))) {
                if (itemSection.isList("actions")) {
                    for (String action : itemSection.getStringList("actions")) {
                        executeAction(player, action);
                    }
                    event.setCancelled(true);
                }
                return;
            }
        }
    }

    private void executeAction(Player player, String action) {
        if (action.startsWith("[MESSAGE]")) {
            MessageUtil.sendFormatted(player, action.substring(9).trim());
        } else if (action.startsWith("[COMMAND]")) {
            player.performCommand(action.substring(9).trim().replace("%player%", player.getName()));
        } else if (action.startsWith("[CONSOLE]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), action.substring(9).trim().replace("%player%", player.getName()));
        } else if (action.startsWith("[OPEN_MENU]")) {
            // Reserved for future GUI menus
            MessageUtil.sendFormatted(player, action.substring(11).trim());
        }
    }
}

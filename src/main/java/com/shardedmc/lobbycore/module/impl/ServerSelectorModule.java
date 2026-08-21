package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ServerSelectorModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private String guiTitle;

    @Override
    public String getId() {
        return "server-selector";
    }

    @Override
    public String getDisplayName() {
        return "Server Selector";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.guiTitle = MessageUtil.plainText(config.getString("gui.title", "&8Server Selector"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    public void openMenu(Player player) {
        int rows = Math.min(6, Math.max(1, config.getInt("gui.rows", 3)));
        Inventory inventory = Bukkit.createInventory(null, rows * 9, MessageUtil.component(config.getString("gui.title", "&8Server Selector")));

        ConfigurationSection servers = config.getConfigurationSection("servers");
        if (servers != null) {
            for (String key : servers.getKeys(false)) {
                ConfigurationSection server = servers.getConfigurationSection(key);
                if (server == null) {
                    continue;
                }
                int slot = server.getInt("slot", 0);
                inventory.setItem(slot, ItemBuilder.fromConfig(server, player));
            }
        }

        if (config.isConfigurationSection("filler")) {
            ItemStack filler = ItemBuilder.fromConfig(config.getConfigurationSection("filler"), player);
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTitle() == null) {
            return;
        }

        String openTitle = MessageUtil.plainText(event.getView().title());
        if (!openTitle.equals(guiTitle)) {
            return;
        }

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) {
            return;
        }

        ConfigurationSection servers = config.getConfigurationSection("servers");
        if (servers == null) {
            return;
        }

        String clickedPlain = MessageUtil.plainText(clicked.getItemMeta().displayName());
        for (String key : servers.getKeys(false)) {
            ConfigurationSection server = servers.getConfigurationSection(key);
            if (server == null) {
                continue;
            }
            String serverPlain = MessageUtil.plainText(MessageUtil.format(server.getString("name", key), player));
            if (!clickedPlain.equalsIgnoreCase(serverPlain)) {
                continue;
            }

            player.closeInventory();
            if (server.contains("command")) {
                String command = server.getString("command").replace("%player%", player.getName());
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
            }
            if (server.contains("message")) {
                MessageUtil.sendFormatted(player, server.getString("message"));
            }
            return;
        }
    }
}

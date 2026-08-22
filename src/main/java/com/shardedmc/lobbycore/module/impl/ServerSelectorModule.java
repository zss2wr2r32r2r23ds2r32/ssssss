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

import java.util.HashMap;
import java.util.Map;

public class ServerSelectorModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private String guiTitle;
    private final Map<Integer, ConfigurationSection> slotServers = new HashMap<>();

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
        reloadSlotMap();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void reloadSlotMap() {
        slotServers.clear();
        ConfigurationSection servers = config.getConfigurationSection("servers");
        if (servers == null) {
            return;
        }
        for (String key : servers.getKeys(false)) {
            ConfigurationSection server = servers.getConfigurationSection(key);
            if (server != null) {
                slotServers.put(server.getInt("slot", -1), server);
            }
        }
    }

    @Override
    public void disable() {
        slotServers.clear();
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

        String openTitle = MessageUtil.plainText(event.getView().title());
        if (!openTitle.equals(guiTitle)) {
            return;
        }

        event.setCancelled(true);
        ConfigurationSection server = slotServers.get(event.getRawSlot());
        if (server == null) {
            return;
        }

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> connectPlayer(player, server));
    }

    private void connectPlayer(Player player, ConfigurationSection server) {
        if (server.contains("console-command")) {
            String command = server.getString("console-command").replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else if (server.contains("command")) {
            String command = server.getString("command").replace("%player%", player.getName());
            if (config.getBoolean("run-as-console", false) || server.getBoolean("run-as-console", false)) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } else {
                Bukkit.dispatchCommand(player, command);
            }
        }

        if (server.contains("message")) {
            MessageUtil.sendFormatted(player, server.getString("message"));
        }
    }
}

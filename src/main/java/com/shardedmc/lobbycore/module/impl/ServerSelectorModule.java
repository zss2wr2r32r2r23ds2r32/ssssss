package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.gui.MenuHolder;
import com.shardedmc.lobbycore.gui.MenuType;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerSelectorModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
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
        reloadSlotMap();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void reloadSlotMap() {
        slotServers.clear();
        ConfigurationSection servers = config.getConfigurationSection("servers");
        if (servers == null) {
            return;
        }

        List<String> keys = new ArrayList<>(servers.getKeys(false));
        int rows = Math.min(6, Math.max(1, config.getInt("gui.rows", 3)));
        int centerRow = (rows / 2) * 9;
        int centerSlot = centerRow + 4;

        for (int i = 0; i < keys.size(); i++) {
            ConfigurationSection server = servers.getConfigurationSection(keys.get(i));
            if (server == null) {
                continue;
            }
            int slot;
            if (config.getBoolean("center-servers", true) && !server.contains("slot")) {
                int startSlot = centerSlot - (keys.size() - 1);
                slot = startSlot + (i * 2);
            } else {
                slot = server.getInt("slot", centerSlot);
            }
            slotServers.put(slot, server);
        }
    }

    @Override
    public void disable() {
        slotServers.clear();
        HandlerList.unregisterAll(this);
    }

    public void openMenu(Player player) {
        int rows = Math.min(6, Math.max(1, config.getInt("gui.rows", 3)));
        MenuHolder holder = new MenuHolder(MenuType.SERVER_SELECTOR);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                MessageUtil.component(config.getString("gui.title", "Server Selector")));
        holder.setInventory(inventory);

        for (Map.Entry<Integer, ConfigurationSection> entry : slotServers.entrySet()) {
            inventory.setItem(entry.getKey(), ItemBuilder.fromConfig(entry.getValue(), player));
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
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || holder.getType() != MenuType.SERVER_SELECTOR) {
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
            player.performCommand(command);
        }

        if (server.contains("message")) {
            MessageUtil.sendFormatted(player, server.getString("message"));
        }
    }
}

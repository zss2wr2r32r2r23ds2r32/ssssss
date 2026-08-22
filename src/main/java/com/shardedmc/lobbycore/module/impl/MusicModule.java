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

public class MusicModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private String guiTitle;
    private final Map<Integer, String> slotActions = new HashMap<>();

    @Override
    public String getId() {
        return "music";
    }

    @Override
    public String getDisplayName() {
        return "Music";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.guiTitle = MessageUtil.plainText(config.getString("gui.title", "&#FF0072Music"));
        slotActions.clear();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        slotActions.clear();
        HandlerList.unregisterAll(this);
    }

    public void openMenu(Player player) {
        int rows = Math.min(6, Math.max(1, config.getInt("gui.rows", 6)));
        Inventory inventory = Bukkit.createInventory(null, rows * 9, MessageUtil.component(config.getString("gui.title", "&#FF0072Music")));
        slotActions.clear();

        ConfigurationSection songs = config.getConfigurationSection("songs");
        if (songs != null) {
            for (String key : songs.getKeys(false)) {
                ConfigurationSection song = songs.getConfigurationSection(key);
                if (song == null) {
                    continue;
                }
                int slot = song.getInt("slot");
                inventory.setItem(slot, ItemBuilder.fromConfig(song, player));
                slotActions.put(slot, "play:" + song.getString("song", key));
            }
        }

        ConfigurationSection controls = config.getConfigurationSection("controls");
        if (controls != null) {
            for (String key : controls.getKeys(false)) {
                ConfigurationSection control = controls.getConfigurationSection(key);
                if (control == null) {
                    continue;
                }
                int slot = control.getInt("slot");
                inventory.setItem(slot, ItemBuilder.fromConfig(control, player));
                slotActions.put(slot, control.getString("action", key));
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
        String action = slotActions.get(event.getRawSlot());
        if (action == null) {
            return;
        }

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> executeAction(player, action));
    }

    private void executeAction(Player player, String action) {
        if (action.startsWith("play:")) {
            String song = action.substring(5);
            runCommand(player, config.getString("commands.play", "music play %song%").replace("%song%", song));
            return;
        }

        String commandPath = "commands." + action.toLowerCase();
        if (config.contains(commandPath)) {
            runCommand(player, config.getString(commandPath));
        }
    }

    private void runCommand(Player player, String commandTemplate) {
        if (commandTemplate == null || commandTemplate.isEmpty()) {
            return;
        }
        String command = commandTemplate.replace("%player%", player.getName());
        if (config.getBoolean("run-as-console", false)) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } else {
            player.performCommand(command);
        }
    }
}

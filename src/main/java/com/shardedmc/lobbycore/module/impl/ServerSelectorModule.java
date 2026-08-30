package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.gui.MenuHolder;
import com.shardedmc.lobbycore.gui.MenuType;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.ItemBuilder;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class ServerSelectorModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private NamespacedKey serverKey;
    private final Map<Integer, String> slotServerKeys = new HashMap<>();
    private final Map<String, ConfigurationSection> serversByKey = new HashMap<>();

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
        this.serverKey = new NamespacedKey(plugin, "server-selector-id");
        reloadSlotMap();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void reloadSlotMap() {
        slotServerKeys.clear();
        serversByKey.clear();
        ConfigurationSection servers = config.getConfigurationSection("servers");
        if (servers == null) {
            return;
        }

        List<String> keys = new ArrayList<>(servers.getKeys(false));
        int rows = Math.min(6, Math.max(1, config.getInt("gui.rows", 3)));
        int centerRow = (rows / 2) * 9;
        int centerSlot = centerRow + 4;

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            ConfigurationSection server = servers.getConfigurationSection(key);
            if (server == null) {
                continue;
            }
            serversByKey.put(key, server);

            int slot;
            if (config.getBoolean("center-servers", true) && !server.contains("slot")) {
                int startSlot = centerSlot - (keys.size() - 1);
                slot = startSlot + (i * 2);
            } else {
                slot = server.getInt("slot", centerSlot);
            }
            slotServerKeys.put(slot, key);
        }
    }

    @Override
    public void disable() {
        slotServerKeys.clear();
        serversByKey.clear();
        HandlerList.unregisterAll(this);
    }

    public void openMenu(Player player) {
        int rows = Math.min(6, Math.max(1, config.getInt("gui.rows", 3)));
        MenuHolder holder = new MenuHolder(MenuType.SERVER_SELECTOR);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9,
                MessageUtil.component(config.getString("gui.title", "Server Selector")));
        holder.setInventory(inventory);

        for (Map.Entry<Integer, String> entry : slotServerKeys.entrySet()) {
            ConfigurationSection server = serversByKey.get(entry.getValue());
            if (server == null) {
                continue;
            }
            inventory.setItem(entry.getKey(), tagServerItem(ItemBuilder.fromConfig(server, player), entry.getValue()));
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

    private ItemStack tagServerItem(ItemStack item, String key) {
        ItemStack tagged = item.clone();
        ItemMeta meta = tagged.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(serverKey, PersistentDataType.STRING, key);
            tagged.setItemMeta(meta);
        }
        return tagged;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder) || holder.getType() != MenuType.SERVER_SELECTOR) {
            return;
        }

        event.setCancelled(true);

        if (event.getClickedInventory() != top) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        String serverId = resolveServerKey(clicked, event.getSlot());
        ConfigurationSection server = serversByKey.get(serverId);
        if (server == null) {
            return;
        }

        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> connectPlayer(player, serverId, server));
    }

    private String resolveServerKey(ItemStack item, int slot) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String key = meta.getPersistentDataContainer().get(serverKey, PersistentDataType.STRING);
            if (key != null) {
                return key;
            }
        }
        return slotServerKeys.get(slot);
    }

    private void connectPlayer(Player player, String serverId, ConfigurationSection server) {
        String target = resolveTargetName(server);

        if (isMaintenance(player, server, target)) {
            String display = server.getString("display-name", target);
            MessageUtil.sendFormatted(player, config.getString("maintenance-message",
                            "&#2BD6FF&lQUEUE &8▷ &#FF0000%server% is currently in maintenance.")
                    .replace("%server%", display));
            return;
        }

        String method = config.getString("connect-method", "auto").toLowerCase();

        if (config.getBoolean("debug", false)) {
            plugin.getLogger().info("Connecting " + player.getName() + " to " + target + " via " + method);
        }

        boolean connected = switch (method) {
            case "bungee" -> connectBungee(player, target);
            case "chat" -> connectChat(player, server, target);
            case "command" -> connectCommand(player, server);
            case "console" -> connectConsole(player, server);
            case "auto" -> connectBungee(player, target);
            default -> connectBungee(player, target);
        };

        if (connected) {
            plugin.getLogger().info("Server selector sent " + player.getName() + " to " + target + " via BungeeCord");
        } else {
            plugin.getLogger().info("Server selector could not connect " + player.getName() + " to " + target);
            MessageUtil.sendFormatted(player, config.getString("connect-failed-message",
                    "%prefix% &#FF2727Could not connect to that server. Please try again or contact staff."));
        }

        if (server.contains("message")) {
            MessageUtil.sendFormatted(player, server.getString("message"));
        }
    }

    private boolean isMaintenance(Player player, ConfigurationSection server, String target) {
        String statusPlaceholder = server.getString("status-placeholder", "");
        if (statusPlaceholder == null || statusPlaceholder.isEmpty()) {
            // Infer from lore placeholders like %shardedvelocitycore_status_diasmp%
            List<String> lore = server.getStringList("lore");
            for (String line : lore) {
                if (line.contains("%shardedvelocitycore_status_")) {
                    int start = line.indexOf("%shardedvelocitycore_status_");
                    int end = line.indexOf('%', start + 1);
                    if (end > start) {
                        statusPlaceholder = line.substring(start, end + 1);
                        break;
                    }
                }
            }
        }
        if (statusPlaceholder == null || statusPlaceholder.isEmpty()) {
            return server.getBoolean("maintenance", false);
        }

        String status = MessageUtil.applyPapi(player, statusPlaceholder);
        if (status == null || status.equals(statusPlaceholder)) {
            return server.getBoolean("maintenance", false);
        }
        String lower = status.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("maintenance") || lower.contains("offline") || lower.contains("closed");
    }

    private String resolveTargetName(ConfigurationSection server) {
        if (server.contains("bungee-name")) {
            return server.getString("bungee-name");
        }
        if (server.contains("command")) {
            String command = server.getString("command").trim();
            String[] parts = command.split(" ");
            if (parts.length >= 2) {
                return parts[parts.length - 1];
            }
        }
        return server.getName();
    }

    private String resolveCommand(ConfigurationSection server, String target) {
        if (server.contains("command")) {
            return server.getString("command");
        }
        return "server " + target;
    }

    private boolean connectBungee(Player player, String serverName) {
        if (serverName == null || serverName.isEmpty()) {
            return false;
        }
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);
            out.writeUTF("Connect");
            out.writeUTF(serverName);
            byte[] payload = stream.toByteArray();
            player.sendPluginMessage(plugin, "BungeeCord", payload);
            player.sendPluginMessage(plugin, "bungeecord:main", payload);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "BungeeCord connect failed for " + player.getName(), ex);
            return false;
        }
    }

    private boolean connectChat(Player player, ConfigurationSection server, String target) {
        String command = resolveCommand(server, target);
        if (command == null || command.isEmpty()) {
            return false;
        }
        player.chat("/" + command);
        return true;
    }

    private boolean connectCommand(Player player, ConfigurationSection server) {
        String command = resolveCommand(server, resolveTargetName(server));
        if (command == null || command.isEmpty()) {
            return false;
        }
        return player.performCommand(command.replace("%player%", player.getName()));
    }

    private boolean connectConsole(Player player, ConfigurationSection server) {
        if (server.contains("console-command")) {
            String command = server.getString("console-command").replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            return true;
        }
        String command = resolveCommand(server, resolveTargetName(server));
        if (command == null || command.isEmpty()) {
            return false;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
        return true;
    }
}

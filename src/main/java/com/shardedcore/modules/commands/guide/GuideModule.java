package com.shardedcore.modules.commands.guide;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ConfigGui;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.HashMap;
import java.util.Map;

public final class GuideModule extends Module implements CommandExecutor, Listener {

    public GuideModule(ShardedCore plugin) {
        super(plugin, "guide");
    }

    @Override
    public void enable() {
        registerListener(this);
        registerCommand("guide", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command.guide")) {
            send(player, "no-permission");
            return true;
        }
        openMenu(player, "main");
        return true;
    }

    private Map<String, String> placeholders() {
        Map<String, String> map = new HashMap<>();
        map.put("discord", config.getString("discord", "discord.gg/shardedmc"));
        map.put("webstore", config.getString("webstore", "store.shardedmc.com"));
        return map;
    }

    private void openMenu(Player player, String menuId) {
        ConfigurationSection menu = config.getConfigurationSection("gui." + menuId);
        if (menu == null) return;
        if (config.getBoolean("play-open-sound", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
        }
        ConfigGui.open(player, menu, "guide-" + menuId, placeholders());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ConfigGui.GuiHolder holder = TrackedInventories.lookup(
                event.getView().getTopInventory(), ConfigGui.GuiHolder.class);
        if (holder == null || !holder.menuId().startsWith("guide-")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String menuId = holder.menuId().substring("guide-".length());
        ConfigurationSection menu = config.getConfigurationSection("gui." + menuId);
        if (menu == null) return;
        String action = ConfigGui.action(menu, event.getSlot());
        if (action == null || action.isBlank()) return;
        if (action.startsWith("menu:")) openMenu(player, action.substring(5));
        else if (action.startsWith("command:")) {
            player.closeInventory();
            Bukkit.dispatchCommand(player, action.substring(8).trim());
        } else if (action.equals("close")) player.closeInventory();
    }
}

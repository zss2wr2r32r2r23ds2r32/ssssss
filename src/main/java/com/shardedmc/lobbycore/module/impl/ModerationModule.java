package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ModerationModule implements Module, Listener, CommandExecutor {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private boolean chatLocked;

    @Override
    public String getId() {
        return "moderation";
    }

    @Override
    public String getDisplayName() {
        return "Moderation";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        chatLocked = config.getBoolean("chat-locked-on-start", false);

        if (plugin.getCommand("clearchat") != null) {
            plugin.getCommand("clearchat").setExecutor(this);
        }
        if (plugin.getCommand("lockchat") != null) {
            plugin.getCommand("lockchat").setExecutor(this);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        if (plugin.getCommand("clearchat") != null) {
            plugin.getCommand("clearchat").setExecutor(null);
        }
        if (plugin.getCommand("lockchat") != null) {
            plugin.getCommand("lockchat").setExecutor(null);
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!chatLocked) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("shardedlobbycore.lockchat")) {
            event.setCancelled(true);
            MessageUtil.sendFormatted(player, config.getString("messages.chat-locked", "&cChat is currently locked."));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("clearchat")) {
            if (!sender.hasPermission("shardedlobbycore.clearchat")) {
                MessageUtil.send(sender, "no-permission");
                return true;
            }

            int lines = config.getInt("clearchat-lines", 100);
            String clearMessage = config.getString("messages.clearchat-broadcast", "&cChat has been cleared by &f%player%")
                    .replace("%player%", sender.getName());

            for (Player player : Bukkit.getOnlinePlayers()) {
                for (int i = 0; i < lines; i++) {
                    player.sendMessage("");
                }
                MessageUtil.sendFormatted(player, clearMessage);
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("lockchat")) {
            if (!sender.hasPermission("shardedlobbycore.lockchat")) {
                MessageUtil.send(sender, "no-permission");
                return true;
            }

            chatLocked = !chatLocked;
            String message = chatLocked ?
                    config.getString("messages.chat-locked-broadcast", "&cChat has been locked by &f%player%") :
                    config.getString("messages.chat-unlocked-broadcast", "&aChat has been unlocked by &f%player%");
            message = message.replace("%player%", sender.getName());

            for (Player player : Bukkit.getOnlinePlayers()) {
                MessageUtil.sendFormatted(player, message);
            }
            return true;
        }

        return false;
    }

    public boolean isChatLocked() {
        return chatLocked;
    }
}

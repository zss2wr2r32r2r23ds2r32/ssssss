package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

public class JoinMessagesModule implements Module, Listener, CommandExecutor {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

    @Override
    public String getId() {
        return "join-messages";
    }

    @Override
    public String getDisplayName() {
        return "Join Messages";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (plugin.getCommand("fly") != null) {
            plugin.getCommand("fly").setExecutor(this);
        }
    }

    @Override
    public void disable() {
        if (plugin.getCommand("fly") != null) {
            plugin.getCommand("fly").setExecutor(null);
        }
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        if (config.getBoolean("set-adventure-mode", true)) {
            player.setGameMode(GameMode.ADVENTURE);
        }

        if (config.getBoolean("disable-vanilla-join-message", true)) {
            event.joinMessage(null);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> sendJoinMessages(player, firstJoin), 5L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        if (config.getBoolean("disable-vanilla-quit-message", true)) {
            event.quitMessage(null);
        }

        if (config.getBoolean("broadcast-quit.enabled", false)) {
            String broadcast = config.getString("broadcast-quit.message", "&c- &7%player%");
            Bukkit.broadcast(MessageUtil.component(MessageUtil.format(broadcast, event.getPlayer())));
        }
    }

    private void sendJoinMessages(Player player, boolean firstJoin) {
        if (config.getBoolean("join-title.enabled", true)) {
            String title = MessageUtil.format(config.getString("join-title.text", "&aWelcome %player%"), player);
            String subtitlePath = player.hasPermission(config.getString("admin-fly.permission", "shardedlobbycore.fly")) &&
                    config.getBoolean("admin-fly.enabled", true) ?
                    "join-title.admin-subtitle" : "join-title.subtitle";
            String subtitle = MessageUtil.format(config.getString(subtitlePath, "&7Use &f/server <name> &7to get started"), player);
            MessageUtil.showTitle(player, title, subtitle,
                    config.getInt("join-title.fade-in", 10),
                    config.getInt("join-title.stay", 60),
                    config.getInt("join-title.fade-out", 10));
        } else if (firstJoin && config.getBoolean("first-join.enabled", true) &&
                config.getBoolean("first-join.title.enabled", true)) {
            String title = MessageUtil.format(config.getString("first-join.title.text", "&aWelcome %player%!"), player);
            String subtitle = MessageUtil.format(config.getString("first-join.title.subtitle", "&7Use &f/server <name> &7to get started"), player);
            MessageUtil.showTitle(player, title, subtitle, 10, 60, 10);
        }

        if (config.getBoolean("admin-fly.enabled", true) &&
                player.hasPermission(config.getString("admin-fly.permission", "shardedlobbycore.fly")) &&
                config.getBoolean("admin-fly.enable-on-join", true)) {
            player.setAllowFlight(true);
        }

        if (config.getBoolean("chat-message.enabled", true)) {
            List<String> lines = firstJoin ?
                    config.getStringList("chat-message.first-join") :
                    config.getStringList("chat-message.returning");

            if (lines.isEmpty()) {
                lines = config.getStringList("chat-message.lines");
            }

            for (String line : lines) {
                MessageUtil.sendFormatted(player, line);
            }
        }

        if (!firstJoin && config.getBoolean("broadcast-join.enabled", false)) {
            String broadcast = config.getString("broadcast-join.message", "&a+ &7%player%");
            Bukkit.broadcast(MessageUtil.component(MessageUtil.format(broadcast, player)));
        } else if (firstJoin && config.getBoolean("broadcast-first-join.enabled", true)) {
            String broadcast = config.getString("broadcast-first-join.message", "&a+ &7%player% &8(First Join)");
            Bukkit.broadcast(MessageUtil.component(MessageUtil.format(broadcast, player)));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "player-only");
            return true;
        }

        if (!player.hasPermission(config.getString("admin-fly.permission", "shardedlobbycore.fly"))) {
            MessageUtil.send(sender, "no-permission");
            return true;
        }

        boolean flying = !player.isFlying();
        player.setAllowFlight(true);
        player.setFlying(flying);
        MessageUtil.sendFormatted(player, flying ?
                config.getString("admin-fly.messages.enabled", "&aFlight enabled.") :
                config.getString("admin-fly.messages.disabled", "&cFlight disabled."));
        return true;
    }
}

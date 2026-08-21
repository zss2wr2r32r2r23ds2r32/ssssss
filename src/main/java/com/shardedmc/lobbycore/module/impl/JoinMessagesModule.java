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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class JoinMessagesModule implements Module, Listener, CommandExecutor {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private final Set<UUID> adminFlying = new HashSet<>();

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
        adminFlying.clear();
        if (plugin.getCommand("fly") != null) {
            plugin.getCommand("fly").setExecutor(null);
        }
        HandlerList.unregisterAll(this);
    }

    public boolean isAdminFlying(Player player) {
        return adminFlying.contains(player.getUniqueId());
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
        adminFlying.remove(event.getPlayer().getUniqueId());

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
            String title = config.getString("join-title.text", "&#AD4EFF&lWelcome &#AD4EFF%player%");
            String subtitlePath = player.hasPermission(config.getString("admin-fly.permission", "shardedlobbycore.fly")) &&
                    config.getBoolean("admin-fly.enabled", true) ?
                    "join-title.admin-subtitle" : "join-title.subtitle";
            String subtitle = config.getString(subtitlePath, "&7Use &f/server <name> &7to get started");
            MessageUtil.showTitle(player, title, subtitle,
                    config.getInt("join-title.fade-in", 10),
                    config.getInt("join-title.stay", 60),
                    config.getInt("join-title.fade-out", 10));
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

        UUID uuid = player.getUniqueId();
        if (adminFlying.contains(uuid)) {
            adminFlying.remove(uuid);
            player.setFlying(false);
            player.setAllowFlight(false);
            MessageUtil.sendFormatted(player, config.getString("admin-fly.messages.disabled", "%prefix% &cFlight disabled."));
        } else {
            adminFlying.add(uuid);
            player.setAllowFlight(true);
            player.setFlying(true);
            MessageUtil.sendFormatted(player, config.getString("admin-fly.messages.enabled", "%prefix% &aFlight enabled."));
        }
        return true;
    }
}

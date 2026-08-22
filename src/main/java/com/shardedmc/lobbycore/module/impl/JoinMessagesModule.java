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

    public void testFirstJoin(Player player) {
        int number = plugin.getJoinCounterManager().peekJoinNumber();
        sendFirstJoinExperience(player, number, true);
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

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (firstJoin) {
                int number = plugin.getJoinCounterManager().nextJoinNumber();
                sendFirstJoinExperience(player, number, false);
            } else {
                sendReturningJoinMessages(player);
            }
        }, 5L);
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

    private void sendFirstJoinExperience(Player player, int joinNumber, boolean testMode) {
        if (config.getBoolean("first-join.title.enabled", true)) {
            String title = config.getString("first-join.title.text", config.getString("join-title.text", "&#AD4EFF&lWelcome &#AD4EFF%player%"));
            String subtitle = config.getString("first-join.title.subtitle", "&fWe are glad to have you at our network!");
            MessageUtil.showTitle(player, title, subtitle,
                    config.getInt("join-title.fade-in", 10),
                    config.getInt("join-title.stay", 60),
                    config.getInt("join-title.fade-out", 10),
                    joinNumber);
        }

        if (config.getBoolean("first-join.chat.enabled", true)) {
            for (String line : config.getStringList("first-join.chat.lines")) {
                MessageUtil.sendFormatted(player, MessageUtil.format(line, player, joinNumber));
            }
        } else if (config.getBoolean("chat-message.enabled", true)) {
            for (String line : config.getStringList("chat-message.first-join")) {
                MessageUtil.sendFormatted(player, MessageUtil.format(line, player, joinNumber));
            }
        }

        if (config.getBoolean("first-join.broadcast.enabled", true)) {
            String broadcast = config.getString("first-join.broadcast.message",
                    "&#FF005D&lNEW &8▷ &7Welcome %player% to ShardedMC you are &8[#%number%]!");
            Bukkit.broadcast(MessageUtil.component(MessageUtil.format(broadcast, player, joinNumber)));
        } else if (!testMode && config.getBoolean("broadcast-first-join.enabled", false)) {
            String broadcast = config.getString("broadcast-first-join.message", "#45FF17+ &f%player%");
            Bukkit.broadcast(MessageUtil.component(MessageUtil.format(broadcast, player, joinNumber)));
        }
    }

    private void sendReturningJoinMessages(Player player) {
        if (config.getBoolean("join-title.enabled", true)) {
            String title = config.getString("join-title.text", "&#AD4EFF&lWelcome &#AD4EFF%player%");
            String subtitle = config.getString("join-title.subtitle", "&fUse command &#AD4EFF/server <name> &fto get started");
            MessageUtil.showTitle(player, title, subtitle,
                    config.getInt("join-title.fade-in", 10),
                    config.getInt("join-title.stay", 60),
                    config.getInt("join-title.fade-out", 10));
        }

        if (config.getBoolean("chat-message.enabled", true)) {
            List<String> lines = config.getStringList("chat-message.returning");
            for (String line : lines) {
                MessageUtil.sendFormatted(player, line);
            }
        }

        if (config.getBoolean("broadcast-join.enabled", false)) {
            String broadcast = config.getString("broadcast-join.message", "&a+ &7%player%");
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
            MessageUtil.sendFormatted(player, config.getString("admin-fly.messages.disabled", "%prefix% &#FF2727Flight disabled."));
        } else {
            adminFlying.add(uuid);
            player.setAllowFlight(true);
            player.setFlying(true);
            MessageUtil.sendFormatted(player, config.getString("admin-fly.messages.enabled", "%prefix% &#9FFF00Flight enabled."));
        }
        return true;
    }
}

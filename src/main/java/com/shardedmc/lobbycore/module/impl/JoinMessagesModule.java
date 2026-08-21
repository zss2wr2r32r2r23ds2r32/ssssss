package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.List;

public class JoinMessagesModule implements Module, Listener {

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
    }

    @Override
    public void disable() {
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

        if (config.getBoolean("disable-vanilla-quit-message", true)) {
            // Handled in quit listener if needed
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
        if (firstJoin && config.getBoolean("first-join.enabled", true)) {
            if (config.getBoolean("first-join.title.enabled", true)) {
                String title = MessageUtil.format(config.getString("first-join.title.text", "&aWelcome %player%!"), player);
                String subtitle = MessageUtil.format(config.getString("first-join.title.subtitle", "&7Use &f/server <name> &7to get started"), player);
                player.showTitle(Title.title(
                        MessageUtil.component(title),
                        MessageUtil.component(subtitle),
                        Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
                ));
            }
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
}

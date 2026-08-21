package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.List;
import java.util.Locale;

public class AntiSwearModule implements Module, Listener {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private List<String> blockedWords;

    @Override
    public String getId() {
        return "anti-swear";
    }

    @Override
    public String getDisplayName() {
        return "Anti Swear";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        blockedWords = config.getStringList("blocked-words").stream()
                .map(w -> w.toLowerCase(Locale.ROOT))
                .toList();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!config.getBoolean("enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.hasPermission("shardedlobbycore.bypass.antiswear")) {
            return;
        }

        String message = event.getMessage().toLowerCase(Locale.ROOT);
        for (String word : blockedWords) {
            if (message.contains(word)) {
                event.setCancelled(true);
                MessageUtil.sendFormatted(player, config.getString("message", "&cPlease do not use inappropriate language."));
                if (config.getBoolean("notify-staff", true)) {
                    String staffMsg = config.getString("staff-message", "&c%player% &7tried to say a blocked word.")
                            .replace("%player%", player.getName())
                            .replace("%word%", word);
                    for (Player staff : Bukkit.getOnlinePlayers()) {
                        if (staff.hasPermission("shardedlobbycore.admin")) {
                            MessageUtil.sendFormatted(staff, staffMsg);
                        }
                    }
                }
                return;
            }
        }
    }
}

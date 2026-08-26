package com.shardedcore.util;

import com.shardedcore.ShardedCore;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {

    public enum MessageMode {
        CHAT,
        ACTIONBAR,
        BOTH;

        public static MessageMode parse(String raw) {
            if (raw == null || raw.isBlank()) return CHAT;
            return switch (raw.trim().toLowerCase().replace('-', '_')) {
                case "actionbar", "action_bar" -> ACTIONBAR;
                case "both" -> BOTH;
                default -> CHAT;
            };
        }
    }

    public enum Delivery {
        CHAT,
        ACTIONBAR,
        BOTH;

        public static Delivery parse(String raw) {
            if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("inherit")) return null;
            return switch (raw.trim().toLowerCase().replace('-', '_')) {
                case "actionbar", "action_bar" -> ACTIONBAR;
                case "both" -> BOTH;
                default -> CHAT;
            };
        }
    }

    private static MessageMode globalMode = MessageMode.CHAT;

    private MessageUtil() {
    }

    public static void reload(ShardedCore plugin) {
        globalMode = MessageMode.parse(plugin.pluginConfig().getString("message-mode", "chat"));
    }

    public static void send(CommandSender to, ShardedCore plugin, String message) {
        deliver(to, Text.c(message), globalMode);
    }

    public static void deliver(CommandSender to, Component component, MessageMode mode) {
        if (mode == null) mode = MessageMode.CHAT;
        if (to instanceof Player player) {
            switch (mode) {
                case ACTIONBAR -> player.sendActionBar(component);
                case BOTH -> {
                    player.sendMessage(component);
                    player.sendActionBar(component);
                }
                default -> to.sendMessage(component);
            }
        } else {
            to.sendMessage(component);
        }
    }

    public static void deliver(CommandSender to, Component component, Delivery mode) {
        if (mode == null) mode = Delivery.CHAT;
        if (to instanceof Player player) {
            switch (mode) {
                case ACTIONBAR -> player.sendActionBar(component);
                case BOTH -> {
                    player.sendMessage(component);
                    player.sendActionBar(component);
                }
                default -> to.sendMessage(component);
            }
        } else {
            to.sendMessage(component);
        }
    }
}

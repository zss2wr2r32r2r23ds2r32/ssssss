package com.sharded.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Sends chat or action bar based on delivery mode. */
public final class MessageUtil {

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

    private MessageUtil() {
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

    public static void deliver(CommandSender to, String legacyMessage, Delivery mode) {
        deliver(to, Text.c(legacyMessage), mode);
    }
}

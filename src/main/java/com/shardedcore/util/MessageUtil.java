package com.shardedcore.util;

import com.shardedcore.ShardedCore;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MessageUtil {

    public enum MessageMode {
        CHAT,
        ACTIONBAR,
        BOTH
    }

    private MessageUtil() {
    }

    public static void reload(ShardedCore plugin) {
        // Reserved for cached message settings.
    }

    public static MessageMode messageMode(String raw) {
        if (raw == null) {
            return MessageMode.CHAT;
        }
        return switch (raw.toLowerCase()) {
            case "actionbar" -> MessageMode.ACTIONBAR;
            case "both" -> MessageMode.BOTH;
            default -> MessageMode.CHAT;
        };
    }

    public static void send(CommandSender sender, ShardedCore plugin, String message) {
        String full = Text.apply(message, "%prefix%", plugin.prefix());
        Component component = Text.component(full, sender instanceof Player player ? player : null);
        MessageMode mode = plugin.messageMode();

        if (mode == MessageMode.CHAT || mode == MessageMode.BOTH) {
            sender.sendMessage(component);
        }
        if ((mode == MessageMode.ACTIONBAR || mode == MessageMode.BOTH) && sender instanceof Player player) {
            player.sendActionBar(component);
        }
    }

    public static void sendRaw(CommandSender sender, String message, Player placeholderPlayer) {
        sender.sendMessage(Text.c(Text.applyPlaceholders(message, placeholderPlayer)));
    }

    public static void sendActionBar(Player player, ShardedCore plugin, String message) {
        player.sendActionBar(Text.cPlain(Text.apply(message, "%prefix%", plugin.prefix())));
    }
}

package com.sharded.core.setup.util;

import com.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;


public final class MessageUtil {

    private MessageUtil() {
    }

    public static boolean isBlank(String message) {
        return message == null || message.isBlank();
    }

    /** Replace %success% / %error% with main-config colours. */
    public static String applyTokens(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }
        ShardedCore plugin = ShardedCore.get();
        String success = plugin != null ? plugin.successColor() : "&#9FFF00";
        String error = plugin != null ? plugin.errorColor() : "&#FF2121";
        return message.replace("%success%", success).replace("%error%", error);
    }

    public static String errorLine(String detail) {
        String body = detail == null ? "" : detail;
        if (body.startsWith("&#FF0000&lERROR")) {
            return applyTokens(body);
        }
        return applyTokens("&#FF0000&lERROR &8▷ &r" + body);
    }

    /** Send chat message only when non-blank (empty config key = skip). */
    public static void sendChatIfPresent(Player player, String message) {
        if (player == null || isBlank(message)) {
            return;
        }
        TextUtil.sendMessage(player, applyTokens(message));
    }

    /** Broadcast chat to all online players only when non-blank. */
    public static void broadcastChatIfPresent(String message) {
        if (isBlank(message)) {
            return;
        }
        String resolved = applyTokens(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage(player, resolved);
        }
    }

    public static String resolveChannel(String moduleChannel) {
        if (moduleChannel != null && !moduleChannel.isBlank()) {
            return moduleChannel.trim().toLowerCase();
        }
        ShardedCore plugin = ShardedCore.get();
        if (plugin == null) {
            return "chat";
        }
        return plugin.getConfig().getString("messages.default-channel", "chat");
    }

    public static String channelFrom(FileConfiguration moduleConfig) {
        if (moduleConfig == null) {
            return resolveChannel(null);
        }
        return resolveChannel(moduleConfig.getString("message-channel"));
    }

    public static void send(Player player, String channel, String message) {
        if (player == null || isBlank(message)) {
            return;
        }
        String resolved = applyTokens(message);
        if ("actionbar".equalsIgnoreCase(channel)) {
            TextUtil.sendActionBar(player, resolved);
        } else {
            TextUtil.sendMessage(player, resolved);
        }
    }

    public static void send(Player player, FileConfiguration moduleConfig, String message) {
        send(player, channelFrom(moduleConfig), message);
    }

    public static void send(CommandSender sender, String channel, String message) {
        if (isBlank(message)) {
            return;
        }
        String resolved = applyTokens(message);
        if (!(sender instanceof Player player)) {
            TextUtil.sendMessage(sender, resolved);
            return;
        }
        send(player, channel, resolved);
    }

    public static void sendActionBar(Player player, String message) {
        if (player == null || isBlank(message)) {
            return;
        }
        TextUtil.sendActionBar(player, applyTokens(message));
    }
}

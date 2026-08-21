package com.shardedmc.lobbycore.util;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MessageUtil {

    private static ShardedLobbyCore plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private MessageUtil() {
    }

    public static void init(ShardedLobbyCore pl) {
        plugin = pl;
    }

    public static String colorize(String text) {
        if (text == null) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static Component component(String text) {
        return LEGACY.deserialize(colorize(text));
    }

    public static String get(String path) {
        String prefix = plugin.getConfig().getString("prefix", "&8[&bShardedLobbyCore&8] &r");
        String message = plugin.getConfigManager().getMessages().getString(path, path);
        return colorize(prefix + message);
    }

    public static String getRaw(String path) {
        return colorize(plugin.getConfigManager().getMessages().getString(path, path));
    }

    public static String format(String text, Player player) {
        if (text == null) {
            return "";
        }
        String result = text.replace("%player%", player.getName());
        if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                result = (String) papi.getMethod("setPlaceholders", Player.class, String.class).invoke(null, player, result);
            } catch (ReflectiveOperationException ignored) {
                // PlaceholderAPI not present at runtime
            }
        }
        return colorize(result);
    }

    public static List<String> formatLore(List<String> lore, Player player) {
        return lore.stream().map(line -> format(line, player)).collect(Collectors.toList());
    }

    public static void send(CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    public static void sendFormatted(Player player, String message) {
        player.sendMessage(format(message, player));
    }
}

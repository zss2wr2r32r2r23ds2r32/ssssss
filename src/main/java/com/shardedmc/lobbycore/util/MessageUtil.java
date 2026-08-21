package com.shardedmc.lobbycore.util;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class MessageUtil {

    private static ShardedLobbyCore plugin;
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern STANDALONE_HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private MessageUtil() {
    }

    public static void init(ShardedLobbyCore pl) {
        plugin = pl;
    }

    public static String getPrefix() {
        return colorize(plugin.getConfig().getString("prefix", "&#00A2FF&lCORE &8▷ &r"));
    }

    public static String colorize(String text) {
        if (text == null) {
            return "";
        }
        text = convertStandaloneHex(text);
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

    private static String convertStandaloneHex(String text) {
        Matcher matcher = STANDALONE_HEX_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        while (matcher.find()) {
            int start = matcher.start();
            if (start > 0 && text.charAt(start - 1) == '&') {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(buffer, "&#" + matcher.group(1));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String applyPlaceholders(String text, Player player) {
        if (text == null) {
            return "";
        }
        String result = text.replace("%prefix%", getPrefix());
        if (player != null) {
            result = result.replace("%player%", player.getName());
            if (plugin.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                    result = (String) papi.getMethod("setPlaceholders", Player.class, String.class).invoke(null, player, result);
                } catch (ReflectiveOperationException ignored) {
                    // PlaceholderAPI not present at runtime
                }
            }
        }
        return colorize(result);
    }

    public static Component component(String text) {
        String colored = colorize(text);
        if (!colored.startsWith("§r")) {
            colored = "§r" + colored;
        }
        return LEGACY.deserialize(colored).decoration(TextDecoration.ITALIC, false);
    }

    public static String plainText(Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
    }

    public static String plainText(String text) {
        return plainText(component(text));
    }

    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(component(applyPlaceholders(message, player)));
    }

    public static void showTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(
                component(applyPlaceholders(title, player)),
                component(applyPlaceholders(subtitle == null ? "" : subtitle, player)),
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L)
                )
        ));
    }

    public static String get(String path) {
        String message = plugin.getConfigManager().getMessages().getString(path, path);
        return applyPlaceholders(message, null);
    }

    public static String getRaw(String path) {
        return applyPlaceholders(plugin.getConfigManager().getMessages().getString(path, path), null);
    }

    public static String format(String text, Player player) {
        return applyPlaceholders(text, player);
    }

    public static List<String> formatLore(List<String> lore, Player player) {
        return lore.stream().map(line -> format(line, player)).collect(Collectors.toList());
    }

    public static void send(CommandSender sender, String path) {
        sender.sendMessage(component(get(path)));
    }

    public static void sendRaw(CommandSender sender, String message) {
        sender.sendMessage(component(message));
    }

    public static void sendFormatted(Player player, String message) {
        player.sendMessage(component(format(message, player)));
    }
}

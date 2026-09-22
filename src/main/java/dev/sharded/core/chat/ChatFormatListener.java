package dev.sharded.core.chat;

import dev.sharded.core.ShardedCore;
import dev.sharded.core.hook.PlaceholderHook;
import dev.sharded.core.util.ColorUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatFormatListener implements Listener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final ShardedCore plugin;

    public ChatFormatListener(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String format = plugin.getConfig().getString("chat.format", "{prefix}{name} &8▷ &r{message}");
        String prefixPlaceholder = plugin.getConfig().getString("chat.prefix-placeholder", "%luckperms_prefix%");
        String suffixPlaceholder = plugin.getConfig().getString("chat.suffix-placeholder", "%luckperms_suffix%");
        event.renderer((source, sourceDisplayName, message, viewer) -> render(
                player, format, prefixPlaceholder, suffixPlaceholder, message
        ));
    }

    private Component render(Player player, String format, String prefixPlaceholder, String suffixPlaceholder, Component message) {
        String prefix = PlaceholderHook.apply(player, prefixPlaceholder);
        String suffix = PlaceholderHook.apply(player, suffixPlaceholder);
        String plainMessage = PLAIN.serialize(message);
        String escaped = MINI.escapeTags(plainMessage);
        String rendered = format
                .replace("{prefix}", prefix == null ? "" : prefix)
                .replace("{suffix}", suffix == null ? "" : suffix)
                .replace("{name}", player.getName())
                .replace("{message}", escaped);
        rendered = PlaceholderHook.apply(player, rendered);
        return ColorUtil.parse(rendered);
    }
}

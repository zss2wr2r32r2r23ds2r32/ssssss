package com.shardedcore.modules.chatformat;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public final class ChatFormatModule extends Module implements Listener {

    public ChatFormatModule(ShardedCore plugin) {
        super(plugin, "chatformat");
    }

    @Override
    public void enable() {
        registerListener(this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String template = config.getString("format", "{prefix}{name} &7▷ &r{message}");
        event.renderer((Player source, Component sourceDisplayName, Component message, Audience viewer) ->
                formatMessage(source, template, message));
    }

    private Component formatMessage(Player player, String template, Component message) {
        String prefix = Text.applyPlaceholders(config.getString("prefix-placeholder", "%luckperms_prefix%"), player);
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        String formatted = template
                .replace("{prefix}", prefix)
                .replace("{name}", player.getName())
                .replace("{message}", plain);
        formatted = Text.applyPlaceholders(formatted, player);
        return Text.c(formatted);
    }
}

package com.sharded.core.modules.chatformat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.PlaceholderUtil;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/** Public chat formatting with LuckPerms prefix via PlaceholderAPI. */
public final class ChatFormatModule extends Module {

    public ChatFormatModule(ShardedCore plugin) {
        super(plugin, "chatformat");
    }

    @Override
    protected void onEnable() {
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String template = config.getString("format", "{prefix}{name} &7▷ &r{message}");
        event.renderer((Player source, Component sourceDisplayName, Component message, Audience viewer) ->
                formatMessage(source, template, message));
    }

    private Component formatMessage(Player player, String template, Component message) {
        String prefix = PlaceholderUtil.apply(player, config.getString("prefix-placeholder", "%luckperms_prefix%"));
        String plain = PlainTextComponentSerializer.plainText().serialize(message);
        String formatted = template
                .replace("{prefix}", prefix)
                .replace("{name}", player.getName())
                .replace("{message}", plain);
        formatted = PlaceholderUtil.apply(player, formatted);
        return Text.c(formatted);
    }
}

package com.shardedcore.modules.chatformat;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.modules.chatcolor.ChatColorModule;
import com.shardedcore.modules.settings.SettingsModule;
import com.shardedcore.modules.tags.TagsModule;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
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

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        SettingsModule settings = plugin.modules().get(SettingsModule.class);
        if (settings != null && !settings.publicChat(player)) {
            event.setCancelled(true);
            player.sendMessage(ColorUtil.parse("&#FF0000&lCHAT &7▷ &fPublic chat is disabled for you. /chattoggle"));
            return;
        }
        event.viewers().removeIf(audience -> {
            if (audience instanceof Player viewer) {
                return settings != null && !settings.publicChat(viewer);
            }
            return false;
        });
        String raw = PlainTextComponentSerializer.plainText().serialize(event.message());
        ChatColorModule colors = plugin.modules().get(ChatColorModule.class);
        if (colors != null) raw = colors.color(player, raw);
        TagsModule tags = plugin.modules().get(TagsModule.class);
        String tag = tags == null ? "" : tags.chatPrefix(player);
        if (tag == null || tag.isBlank()) {
            tag = Text.applyPlaceholders(cfg("tag-placeholder", "%shardedcore_tag%"), player);
            if (tag.contains("%shardedcore_tag%")) tag = "";
        }
        String format = cfg("format", "{prefix}{tag}{name} &8▷ &r{message}");
        String prefix = Text.applyPlaceholders(cfg("prefix-placeholder", "%luckperms_prefix%"), player);
        String suffix = Text.applyPlaceholders(cfg("suffix-placeholder", "%luckperms_suffix%"), player);
        String rendered = format
                .replace("{prefix}", prefix)
                .replace("{suffix}", suffix)
                .replace("{tag}", tag)
                .replace("{name}", player.getName())
                .replace("{displayname}", player.getName())
                .replace("{message}", raw);
        Component component = ColorUtil.parse(Text.applyPlaceholders(rendered, player));
        event.renderer((source, sourceDisplayName, message, viewer) -> component);
    }
}

package com.sharded.core.modules.chatmoderation;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.WordBlacklist;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/** Anti-swear word filter. */
public final class ChatModerationModule extends Module {

    public ChatModerationModule(ShardedCore plugin) {
        super(plugin, "chatmoderation");
    }

    @Override
    protected void onEnable() {
        registerListener(this);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("sharded.chatmoderation.bypass")) return;
        if (!config.getBoolean("anti-swear.enabled", true)) return;

        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (WordBlacklist.contains(config, "swear-words", text)) {
            event.setCancelled(true);
            send(player, "swear-blocked");
        }
    }
}

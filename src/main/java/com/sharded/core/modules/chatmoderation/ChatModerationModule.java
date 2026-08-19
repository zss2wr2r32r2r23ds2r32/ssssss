package com.sharded.core.modules.chatmoderation;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.WordBlacklist;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Anti-swear, anti-spam, and spammy command cooldowns. */
public final class ChatModerationModule extends Module {

    private final Map<UUID, Long> lastMessage = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> repeatCount = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastText = new ConcurrentHashMap<>();
    private final Map<UUID, Long> commandCooldown = new ConcurrentHashMap<>();

    public ChatModerationModule(ShardedCore plugin) {
        super(plugin, "chatmoderation");
    }

    @Override
    protected void onEnable() {
        registerListener(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("sharded.chatmoderation.bypass")) return;

        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (config.getBoolean("anti-swear.enabled", true)) {
            if (WordBlacklist.contains(config, "swear-words", text)) {
                event.setCancelled(true);
                send(player, "swear-blocked");
                return;
            }
        }

        if (config.getBoolean("anti-spam.enabled", true)) {
            long cooldownMs = config.getLong("anti-spam.cooldown-ms", 1500L);
            Long last = lastMessage.get(uuid);
            if (last != null && now - last < cooldownMs) {
                event.setCancelled(true);
                send(player, "spam-cooldown", "%time%", String.valueOf((cooldownMs - (now - last)) / 1000L + 1));
                return;
            }
            lastMessage.put(uuid, now);

            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals(lastText.getOrDefault(uuid, ""))) {
                int count = repeatCount.merge(uuid, 1, Integer::sum);
                if (count >= config.getInt("anti-spam.repeat-limit", 3)) {
                    event.setCancelled(true);
                    send(player, "repeat-spam");
                    return;
                }
            } else {
                repeatCount.put(uuid, 0);
                lastText.put(uuid, normalized);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!config.getBoolean("anti-spam-commands.enabled", true)) return;
        Player player = event.getPlayer();
        if (player.hasPermission("sharded.chatmoderation.bypass")) return;

        String body = event.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        List<String> watched = config.getStringList("anti-spam-commands.commands");
        boolean match = watched.stream().anyMatch(c -> c.equalsIgnoreCase(body));
        if (!match) return;

        long cooldownMs = config.getLong("anti-spam-commands.cooldown-ms", 3000L);
        long now = System.currentTimeMillis();
        Long last = commandCooldown.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            event.setCancelled(true);
            send(player, "command-spam", "%command%", body);
            return;
        }
        commandCooldown.put(player.getUniqueId(), now);
    }
}

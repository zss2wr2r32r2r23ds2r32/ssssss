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
import org.bukkit.event.player.PlayerQuitEvent;

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
    private final Map<UUID, Long> lastChatWarn = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCommandWarn = new ConcurrentHashMap<>();

    public ChatModerationModule(ShardedCore plugin) {
        super(plugin, "chatmoderation");
    }

    @Override
    protected void onEnable() {
        registerListener(this);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastMessage.remove(uuid);
        repeatCount.remove(uuid);
        lastText.remove(uuid);
        commandCooldown.remove(uuid);
        lastChatWarn.remove(uuid);
        lastCommandWarn.remove(uuid);
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
                warnChat(player, "swear-blocked", warnCooldownMs());
                return;
            }
        }

        if (config.getBoolean("anti-spam.enabled", true)) {
            long cooldownMs = config.getLong("anti-spam.cooldown-ms", 1500L);
            Long last = lastMessage.get(uuid);
            if (last != null && now - last < cooldownMs) {
                event.setCancelled(true);
                long secondsLeft = (cooldownMs - (now - last)) / 1000L + 1;
                warnChat(player, "spam-cooldown", warnCooldownMs(), "%time%", String.valueOf(secondsLeft));
                return;
            }
            lastMessage.put(uuid, now);

            String normalized = text.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals(lastText.getOrDefault(uuid, ""))) {
                int count = repeatCount.merge(uuid, 1, Integer::sum);
                if (count >= config.getInt("anti-spam.repeat-limit", 3)) {
                    event.setCancelled(true);
                    warnChat(player, "repeat-spam", warnCooldownMs());
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

        String raw = event.getMessage().substring(1).trim();
        if (raw.isEmpty()) return;
        String label = raw.split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        final String commandLabel = label;

        List<String> watched = config.getStringList("anti-spam-commands.commands");
        boolean match = watched.stream().anyMatch(c -> c.equalsIgnoreCase(commandLabel));
        if (!match) return;

        long cooldownMs = config.getLong("anti-spam-commands.cooldown-ms", 3000L);
        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();
        Long last = commandCooldown.get(uuid);
        if (last != null && now - last < cooldownMs) {
            event.setCancelled(true);
            long secondsLeft = (cooldownMs - (now - last)) / 1000L + 1;
            warnCommand(player, "command-spam", warnCooldownMs(), "%command%", commandLabel, "%time%", String.valueOf(secondsLeft));
            return;
        }
        commandCooldown.put(uuid, now);
    }

    private long warnCooldownMs() {
        return config.getLong("warn-cooldown-ms", 2500L);
    }

    private void warnChat(Player player, String key, long throttleMs, String... replacements) {
        if (!shouldWarn(player.getUniqueId(), lastChatWarn, throttleMs)) return;
        send(player, key, replacements);
    }

    private void warnCommand(Player player, String key, long throttleMs, String... replacements) {
        if (!shouldWarn(player.getUniqueId(), lastCommandWarn, throttleMs)) return;
        send(player, key, replacements);
    }

    private boolean shouldWarn(UUID uuid, Map<UUID, Long> lastWarn, long throttleMs) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(uuid);
        if (last != null && now - last < throttleMs) return false;
        lastWarn.put(uuid, now);
        return true;
    }
}

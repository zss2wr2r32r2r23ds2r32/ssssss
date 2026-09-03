package com.shardedcore.eventcore.service;

import com.shardedcore.eventcore.ShardedEventCore;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Captures a player's next chat message and hands it to a callback.
 *
 * <p>Used by the world-border icon, which asks for {@code <size> <duration>} in
 * chat. Prompts expire lazily on the next interaction rather than from a
 * repeating task, so an idle server does nothing at all for this feature.</p>
 */
public final class ChatPromptService implements Listener {

    private record Prompt(Consumer<String> onInput, Runnable onCancel, long expiresAtMillis) {
    }

    private final ShardedEventCore plugin;
    private final Map<UUID, Prompt> pending = new ConcurrentHashMap<>();

    public ChatPromptService(ShardedEventCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers a one-shot chat listener for {@code player}.
     *
     * @param onInput  run on the main thread with the raw message
     * @param onCancel run on the main thread if the player types the cancel word
     */
    public void await(Player player, long timeoutSeconds, Consumer<String> onInput, Runnable onCancel) {
        pending.put(player.getUniqueId(), new Prompt(onInput, onCancel,
                System.currentTimeMillis() + Math.max(1L, timeoutSeconds) * 1000L));
    }

    public boolean isAwaiting(Player player) {
        Prompt prompt = pending.get(player.getUniqueId());
        if (prompt == null) {
            return false;
        }
        if (System.currentTimeMillis() > prompt.expiresAtMillis()) {
            pending.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    public void cancelAll() {
        pending.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Prompt prompt = pending.remove(uuid);
        if (prompt == null) {
            return;
        }
        if (System.currentTimeMillis() > prompt.expiresAtMillis()) {
            // Expired prompts must not swallow a normal chat message.
            return;
        }
        event.setCancelled(true);

        String raw = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String cancelWord = plugin.mainConfig().raw().getString("prompts.cancel-word", "cancel");

        // Chat is delivered off the main thread; every callback touches the API.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (raw.equalsIgnoreCase(cancelWord)) {
                if (prompt.onCancel() != null) {
                    prompt.onCancel().run();
                }
                return;
            }
            prompt.onInput().accept(raw);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}

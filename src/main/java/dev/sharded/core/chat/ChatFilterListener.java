package dev.sharded.core.chat;

import dev.sharded.core.ShardedCore;
import dev.sharded.core.chat.filter.FilterVerdict;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public final class ChatFilterListener implements Listener {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final ShardedCore plugin;

    public ChatFilterListener(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!plugin.chatFilter().config().scanChat) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission("shardedcore.chatfilter.bypass")) {
            return;
        }
        String plain = PLAIN.serialize(event.message());
        FilterVerdict verdict = plugin.chatFilter().check(player, plain);
        if (verdict.blocked()) {
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.chatFilter().apply(player, verdict));
            return;
        }
        if (verdict.modified()) {
            event.message(Component.text(verdict.result()));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("shardedcore.chatfilter.bypass")) {
            return;
        }
        String raw = event.getMessage();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        int space = raw.indexOf(' ');
        String label = space < 0 ? raw : raw.substring(0, space);
        String rest = space < 0 ? "" : raw.substring(space + 1);
        boolean privateMessage = plugin.chatFilter().config().scanPrivateMessages && plugin.chatFilter().isPrivateMessage(label);
        boolean scannedCommand = plugin.chatFilter().scansCommand(label);
        if (!privateMessage && !scannedCommand) {
            return;
        }
        String payload = rest;
        if (privateMessage) {
            int next = rest.indexOf(' ');
            payload = next < 0 ? rest : rest.substring(next + 1);
        }
        if (payload.isBlank()) {
            return;
        }
        FilterVerdict verdict = plugin.chatFilter().check(player, payload);
        if (verdict.blocked()) {
            event.setCancelled(true);
            plugin.chatFilter().apply(player, verdict);
            return;
        }
        if (verdict.modified()) {
            if (privateMessage) {
                int next = rest.indexOf(' ');
                String target = next < 0 ? rest : rest.substring(0, next);
                event.setMessage("/" + label + " " + target + " " + verdict.result());
            } else {
                event.setMessage("/" + label + " " + verdict.result());
            }
        }
    }
}

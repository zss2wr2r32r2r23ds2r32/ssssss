package com.sharded.core.modules.chat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Public chat toggle. Players who toggled chat off no longer see public chat.
 * State is persisted in players.yml and also editable from /settings.
 */
public final class ChatToggleModule extends Module implements CommandExecutor {

    public static final String STATE_KEY = "chat-enabled";

    public ChatToggleModule(ShardedCore plugin) {
        super(plugin, "chat");
    }

    @Override
    protected void onEnable() {
        registerCommand("chattoggle", this);
    }

    public boolean isChatEnabled(Player player) {
        return plugin.stateStore().getBool(player.getUniqueId(), STATE_KEY, true);
    }

    public void setChatEnabled(Player player, boolean enabled) {
        plugin.stateStore().setBool(player.getUniqueId(), STATE_KEY, enabled);
        send(player, enabled ? "chat-enabled" : "chat-disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("toggle")) {
            return true;
        }
        if (!player.hasPermission("sharded.chat.toggle")) {
            send(player, "no-permission");
            return true;
        }
        setChatEnabled(player, !isChatEnabled(player));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.viewers().removeIf(viewer ->
                viewer instanceof Player player && !isChatEnabled(player) && player != event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMentions(AsyncChatEvent event) {
        if (!config.getBoolean("mentions.enabled", true)) return;
        String plain = PlainTextComponentSerializer.plainText().serialize(event.message());
        Component message = Component.text(plain);
        for (Player online : Bukkit.getOnlinePlayers()) {
            String name = online.getName();
            if (name == null || name.isBlank() || !plain.contains(name)) continue;
            message = underlineAll(message, name);
            plain = PlainTextComponentSerializer.plainText().serialize(message);
        }
        event.message(message);
    }

    private Component underlineAll(Component source, String name) {
        String plain = PlainTextComponentSerializer.plainText().serialize(source);
        int idx = plain.indexOf(name);
        if (idx < 0) return source;
        return Component.text(plain.substring(0, idx))
                .append(Component.text(name).decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.suggestCommand(name)))
                .append(underlineAll(Component.text(plain.substring(idx + name.length())), name));
    }
}

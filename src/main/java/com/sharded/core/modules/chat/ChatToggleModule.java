package com.sharded.core.modules.chat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import io.papermc.paper.event.player.AsyncChatEvent;
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
}

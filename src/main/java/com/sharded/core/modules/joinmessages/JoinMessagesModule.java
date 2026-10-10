package com.sharded.core.modules.joinmessages;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.PlayerToggles;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Custom join/quit messages per rank. Works exactly like the death messages
 * module: each format has a LuckPerms-grantable permission and a priority.
 *
 * Placeholders: %player%, %rank% (LuckPerms prefix), %group%
 */
public final class JoinMessagesModule extends Module {

    public JoinMessagesModule(ShardedCore plugin) {
        super(plugin, "joinmessages");
    }

    @Override
    protected void onEnable() {
    }

    private String resolve(Player player, String type) {
        ConfigurationSection formats = config.getConfigurationSection("formats");
        if (formats == null) return null;

        ConfigurationSection best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (String key : formats.getKeys(false)) {
            ConfigurationSection format = formats.getConfigurationSection(key);
            if (format == null) continue;
            String permission = format.getString("permission", "");
            if (!permission.isEmpty() && !player.hasPermission(permission)) continue;
            int priority = format.getInt("priority", 0);
            if (priority > bestPriority) {
                bestPriority = priority;
                best = format;
            }
        }
        if (best == null) return null;
        String message = best.getString(type, "");
        if (message == null || message.isEmpty()) return null;
        return Text.apply(message,
                "%player%", player.getName(),
                "%rank%", plugin.luckPerms().prefix(player),
                "%group%", plugin.luckPerms().primaryGroup(player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String type = player.hasPlayedBefore() ? "join" : "first-join";
        String message = resolve(player, type);
        if (message == null && type.equals("first-join")) message = resolve(player, "join");
        if (message != null) {
            event.joinMessage(null);
            var component = Text.c(message);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (PlayerToggles.joinMessages(viewer)) viewer.sendMessage(component);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        String message = resolve(event.getPlayer(), "quit");
        if (message != null) {
            event.quitMessage(null);
            var component = Text.c(message);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (PlayerToggles.joinMessages(viewer)) viewer.sendMessage(component);
            }
        }
    }
}

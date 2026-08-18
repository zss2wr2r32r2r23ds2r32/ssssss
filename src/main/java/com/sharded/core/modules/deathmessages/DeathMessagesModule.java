package com.sharded.core.modules.deathmessages;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Custom death messages per rank. Every format has a permission you can give
 * through LuckPerms (e.g. /lp group vip permission set sharded.deathmessages.vip)
 * and the highest-priority format the player has permission for is used.
 *
 * Placeholders: %player%, %rank% (LuckPerms prefix), %group%, %killer%, %killer_rank%
 */
public final class DeathMessagesModule extends Module {

    public DeathMessagesModule(ShardedCore plugin) {
        super(plugin, "deathmessages");
    }

    @Override
    protected void onEnable() {
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        ConfigurationSection formats = config.getConfigurationSection("formats");
        if (formats == null) return;

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
        if (best == null) return;

        Player killer = player.getKiller();
        String message;
        if (killer != null && !killer.equals(player)) {
            message = best.getString("killed-by-player", best.getString("death", ""));
        } else {
            message = best.getString("death", "");
        }
        if (message == null || message.isEmpty()) return;

        message = Text.apply(message,
                "%player%", player.getName(),
                "%rank%", plugin.luckPerms().prefix(player),
                "%group%", plugin.luckPerms().primaryGroup(player),
                "%killer%", killer == null ? "" : killer.getName(),
                "%killer_rank%", killer == null ? "" : plugin.luckPerms().prefix(killer));
        event.deathMessage(null);
        var component = Text.c(message);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (com.sharded.core.util.PlayerToggles.deathMessages(viewer)) {
                viewer.sendMessage(component);
            }
        }
    }
}

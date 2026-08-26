package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.tokens.TokenService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class EventRewards {

    private EventRewards() {
    }

    public static void grant(ShardedCore plugin, UUID uuid, ConfigurationSection section) {
        if (section == null) return;
        long tokens = section.getLong("tokens", 0);
        TokenService service = plugin.modules().tokens();
        if (service != null && tokens > 0) service.give(uuid, tokens);

        Player online = Bukkit.getPlayer(uuid);
        String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) name = uuid.toString();

        for (String cmd : section.getStringList("commands")) {
            if (cmd == null || cmd.isBlank()) continue;
            String resolved = cmd.replace("%player%", name).replace("%player_name%", name).replace("%uuid%", uuid.toString());
            if (resolved.startsWith("[console]")) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved.substring("[console]".length()).trim());
            } else if (online != null) {
                online.performCommand(resolved.startsWith("/") ? resolved.substring(1) : resolved);
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved.startsWith("/") ? resolved.substring(1) : resolved);
            }
        }
    }
}

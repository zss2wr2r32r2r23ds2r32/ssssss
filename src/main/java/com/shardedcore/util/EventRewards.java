package com.shardedcore.util;

import com.shardedcore.ShardedCore;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.modules.economy.EconomyService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class EventRewards {

    private EventRewards() {}

    public static void grant(ShardedCore plugin, UUID uuid, ConfigurationSection section) {
        if (section == null) return;
        long money = section.getLong("money", section.getLong("economy", 0L));
        EconomyModule economyModule = plugin.modules().get(EconomyModule.class);
        if (economyModule != null && money > 0) {
            EconomyService service = economyModule.service();
            if (service != null) service.add(uuid, money);
        }
        Player online = Bukkit.getPlayer(uuid);
        String name = online != null ? online.getName() : Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) name = uuid.toString();
        for (String cmd : section.getStringList("commands")) {
            if (cmd == null || cmd.isBlank()) continue;
            String resolved = cmd.replace("%player%", name).replace("%player_name%", name);
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

package com.shardedcore.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class LuckPermsHelper {

    private LuckPermsHelper() {}

    public static String prefix(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return "";
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                    .invoke(userManager, player.getUniqueId());
            if (user == null) return "";
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
            return prefix == null ? "" : prefix.toString();
        } catch (ReflectiveOperationException ignored) {
            return "";
        }
    }

    public static String primaryGroup(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) return "default";
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class)
                    .invoke(userManager, player.getUniqueId());
            if (user == null) return "default";
            Object group = user.getClass().getMethod("getPrimaryGroup").invoke(user);
            return group == null ? "default" : group.toString();
        } catch (ReflectiveOperationException ignored) {
            return "default";
        }
    }
}

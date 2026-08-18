package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

/**
 * Soft hook into LuckPerms. Used to resolve %rank% / %prefix% / %suffix%
 * placeholders in join and death messages. Falls back to empty strings
 * when LuckPerms is not installed.
 */
public final class LuckPermsHook {

    private final ShardedCore plugin;
    private boolean available;

    public LuckPermsHook(ShardedCore plugin) {
        this.plugin = plugin;
        try {
            Class.forName("net.luckperms.api.LuckPermsProvider");
            LuckPermsProvider.get();
            available = true;
            plugin.getLogger().info("Hooked into LuckPerms.");
        } catch (Throwable ignored) {
            available = false;
            plugin.getLogger().info("LuckPerms not found - rank placeholders will be empty.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public String prefix(Player player) {
        return meta(player, true);
    }

    public String suffix(Player player) {
        return meta(player, false);
    }

    public String primaryGroup(Player player) {
        if (!available) return "";
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            return user == null ? "" : user.getPrimaryGroup();
        } catch (Throwable t) {
            return "";
        }
    }

    private String meta(Player player, boolean prefix) {
        if (!available) return "";
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user == null) return "";
            CachedMetaData meta = user.getCachedData().getMetaData();
            String value = prefix ? meta.getPrefix() : meta.getSuffix();
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }
}

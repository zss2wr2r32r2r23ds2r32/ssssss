package com.sharded.core.hook;

import com.sharded.core.ShardedCore;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Soft hook into LuckPerms. Used to resolve rank placeholders and temp-rank shop checks.
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

    public String prefix(UUID uuid) {
        if (!available) return "";
        try {
            User user = user(uuid);
            if (user == null) return "";
            CachedMetaData meta = user.getCachedData().getMetaData();
            String value = meta.getPrefix();
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }

    public String suffix(Player player) {
        return meta(player, false);
    }

    public String primaryGroup(Player player) {
        if (!available) return "";
        try {
            User user = user(player.getUniqueId());
            return user == null ? "" : user.getPrimaryGroup();
        } catch (Throwable t) {
            return "";
        }
    }

    /** True when the player has a non-expiring inheritance node for the group. */
    public boolean hasPermanentGroup(UUID uuid, String group) {
        if (!available) return false;
        User user = user(uuid);
        if (user == null) return false;
        String target = group.toLowerCase(Locale.ROOT);
        return user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(node -> node.getGroupName().equalsIgnoreCase(target) && !node.hasExpiry());
    }

    /** True when the player has an unexpired temporary inheritance node for the group. */
    public boolean hasActiveTempGroup(UUID uuid, String group) {
        if (!available) return false;
        User user = user(uuid);
        if (user == null) return false;
        String target = group.toLowerCase(Locale.ROOT);
        return user.getNodes(NodeType.INHERITANCE).stream()
                .anyMatch(node -> node.getGroupName().equalsIgnoreCase(target)
                        && node.hasExpiry()
                        && !node.hasExpired());
    }

    /** Remaining time on a temp rank, if any. */
    public Optional<Duration> tempGroupTimeLeft(UUID uuid, String group) {
        if (!available) return Optional.empty();
        User user = user(uuid);
        if (user == null) return Optional.empty();
        String target = group.toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        return user.getNodes(NodeType.INHERITANCE).stream()
                .filter(node -> node.getGroupName().equalsIgnoreCase(target) && node.hasExpiry() && !node.hasExpired())
                .map(node -> Duration.between(now, node.getExpiry()))
                .filter(d -> !d.isNegative() && !d.isZero())
                .findFirst();
    }

    /** Highest permanent rank from ordered list (last matching index wins). */
    public Optional<String> highestPermanentRank(UUID uuid, List<String> orderedRanks) {
        if (!available || orderedRanks == null) return Optional.empty();
        String found = null;
        for (String rank : orderedRanks) {
            if (hasPermanentGroup(uuid, rank)) found = rank;
        }
        return Optional.ofNullable(found);
    }

    public int rankIndex(List<String> orderedRanks, String rank) {
        if (orderedRanks == null || rank == null) return -1;
        for (int i = 0; i < orderedRanks.size(); i++) {
            if (orderedRanks.get(i).equalsIgnoreCase(rank)) return i;
        }
        return -1;
    }

    public boolean runConsole(String command) {
        return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private User user(UUID uuid) {
        try {
            LuckPerms lp = LuckPermsProvider.get();
            User user = lp.getUserManager().getUser(uuid);
            if (user != null) return user;
            return lp.getUserManager().loadUser(uuid).join();
        } catch (Throwable t) {
            return null;
        }
    }

    private String meta(Player player, boolean prefix) {
        if (!available) return "";
        try {
            User user = user(player.getUniqueId());
            if (user == null) return "";
            CachedMetaData meta = user.getCachedData().getMetaData();
            String value = prefix ? meta.getPrefix() : meta.getSuffix();
            return value == null ? "" : value;
        } catch (Throwable t) {
            return "";
        }
    }
}

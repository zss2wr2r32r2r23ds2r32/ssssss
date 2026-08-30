package com.shardedcore.data;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Sqlite;
import com.shardedcore.util.Amounts;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/** Timed crystal-shop perks stored in SQLite, with optional LuckPerms temp nodes. */
public final class TimedPerks {

    private TimedPerks() {
    }

    public static void ensureTable(Sqlite sqlite) {
        try {
            sqlite.run("""
                    CREATE TABLE IF NOT EXISTS timed_perks (
                        uuid TEXT NOT NULL,
                        perk TEXT NOT NULL,
                        expires INTEGER NOT NULL,
                        PRIMARY KEY (uuid, perk)
                    )
                    """);
        } catch (SQLException ex) {
            ShardedCore.get().getLogger().log(Level.SEVERE, "Failed to create timed_perks table", ex);
        }
    }

    public static boolean grant(Player player, String perk, String duration, String luckPermsNode) {
        if (player == null || perk == null || perk.isBlank()) return false;
        long expires = expiresAt(duration);
        Sqlite sqlite = ShardedCore.get().toggles().sqlite();
        ensureTable(sqlite);
        try {
            sqlite.execute("""
                    INSERT INTO timed_perks (uuid, perk, expires) VALUES (?, ?, ?)
                    ON CONFLICT(uuid, perk) DO UPDATE SET expires = excluded.expires
                    """, player.getUniqueId().toString(), perk.toLowerCase(), expires);
        } catch (SQLException ex) {
            ShardedCore.get().getLogger().log(Level.WARNING, "Failed to save perk " + perk, ex);
            return false;
        }
        if (luckPermsNode != null && !luckPermsNode.isBlank() && Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + player.getName() + " permission settemp " + luckPermsNode + " true " + duration);
        }
        apply(player);
        return true;
    }

    public static void apply(Player player) {
        if (player == null) return;
        ShardedCore plugin = ShardedCore.get();
        Map<String, String> nodes = Map.of(
                "ec", "shardedcore.ec",
                "craft", "shardedcore.craft",
                "fly", "shardedcore.fly.bypass"
        );
        for (Map.Entry<String, String> entry : nodes.entrySet()) {
            if (!has(player.getUniqueId(), entry.getKey())) continue;
            player.addAttachment(plugin, entry.getValue(), true);
            if (entry.getKey().equals("fly")) player.addAttachment(plugin, "shardedcore.fly", true);
        }
    }

    public static boolean has(UUID uuid, String perk) {
        if (uuid == null || perk == null) return false;
        Sqlite sqlite = ShardedCore.get().toggles().sqlite();
        ensureTable(sqlite);
        try {
            Long expires = sqlite.query("SELECT expires FROM timed_perks WHERE uuid = ? AND perk = ?", rs -> {
                try {
                    return rs.next() ? rs.getLong("expires") : null;
                } catch (SQLException ex) {
                    return null;
                }
            }, uuid.toString(), perk.toLowerCase());
            if (expires == null) return false;
            if (expires > 0 && expires < System.currentTimeMillis()) {
                sqlite.execute("DELETE FROM timed_perks WHERE uuid = ? AND perk = ?", uuid.toString(), perk.toLowerCase());
                return false;
            }
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private static long expiresAt(String duration) {
        if (duration == null || duration.isBlank() || duration.equalsIgnoreCase("permanent") || duration.equalsIgnoreCase("perm")) {
            return 0L;
        }
        long millis = Amounts.durationMillis(duration);
        return millis <= 0 ? 0L : System.currentTimeMillis() + millis;
    }
}

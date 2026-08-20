package com.sharded.core.modules.punishments;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PunishmentDatabase {

    public record AltAccount(String name, UUID uuid) {
    }

    public enum PunishmentType {
        BAN, MUTE, IP_BAN, WARN, KICK
    }

    public record PunishmentRecord(
            long id,
            UUID uuid,
            String playerName,
            String staffName,
            PunishmentType type,
            String reason,
            long createdAt,
            Long expiresAt,
            boolean active,
            String ip
    ) {
        public boolean isExpired() {
            return expiresAt != null && expiresAt > 0 && System.currentTimeMillis() >= expiresAt;
        }

        public boolean isPermanent() {
            return expiresAt == null || expiresAt <= 0;
        }
    }

    private final ShardedCore plugin;
    private Connection connection;

    public PunishmentDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "punishments.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS punishments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        staff_uuid TEXT,
                        staff_name TEXT NOT NULL,
                        type TEXT NOT NULL,
                        reason TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER,
                        active INTEGER NOT NULL DEFAULT 1,
                        ip TEXT,
                        doxxed INTEGER NOT NULL DEFAULT 0
                    )
                    """);
            try {
                statement.execute("ALTER TABLE punishments ADD COLUMN doxxed INTEGER NOT NULL DEFAULT 0");
            } catch (SQLException ignored) {
            }
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS player_ips (
                        uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        ip TEXT NOT NULL,
                        last_seen INTEGER NOT NULL,
                        PRIMARY KEY (uuid, ip)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ip_bans (
                        ip TEXT PRIMARY KEY,
                        reason TEXT NOT NULL,
                        staff_name TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER,
                        active INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON punishments(uuid)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_ip ON punishments(ip)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_player_ips_ip ON player_ips(ip)");
        }
    }

    public synchronized void recordIp(UUID uuid, String playerName, String ip) {
        if (ip == null || ip.isBlank()) return;
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_ips (uuid, player_name, ip, last_seen) VALUES (?, ?, ?, ?)
                ON CONFLICT(uuid, ip) DO UPDATE SET
                    player_name = excluded.player_name,
                    last_seen = excluded.last_seen
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, ip);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[staff] Failed to record IP: " + e.getMessage());
        }
    }

    public synchronized List<AltAccount> findAlts(String ip, UUID exclude) {
        List<AltAccount> alts = new ArrayList<>();
        if (ip == null || ip.isBlank()) return alts;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, player_name FROM player_ips WHERE ip = ? AND uuid != ? ORDER BY last_seen DESC")) {
            ps.setString(1, ip);
            ps.setString(2, exclude.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    alts.add(new AltAccount(rs.getString("player_name"), UUID.fromString(rs.getString("uuid"))));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to lookup alts: " + e.getMessage());
        }
        return alts;
    }

    public synchronized boolean isDoxxed(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM punishments WHERE uuid = ? AND doxxed = 1 AND active = 1 LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized boolean hasBannedAltOnIp(String ip, UUID joiningUuid) {
        if (ip == null || ip.isBlank()) return false;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1 FROM player_ips pi
                JOIN punishments p ON p.uuid = pi.uuid
                WHERE pi.ip = ? AND pi.uuid != ? AND p.type = 'BAN' AND p.active = 1
                LIMIT 1
                """)) {
            ps.setString(1, ip);
            ps.setString(2, joiningUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void markDoxxed(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET doxxed = 1 WHERE uuid = ? AND type = 'BAN' AND active = 1")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public synchronized String latestIp(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT ip FROM player_ips WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("ip") : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized long addPunishment(UUID uuid, String playerName, UUID staffUuid, String staffName,
                                           PunishmentType type, String reason, Long expiresAt, String ip, boolean doxxed) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO punishments (uuid, player_name, staff_uuid, staff_name, type, reason, created_at, expires_at, active, ip, doxxed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setString(3, staffUuid == null ? null : staffUuid.toString());
            ps.setString(4, staffName);
            ps.setString(5, type.name());
            ps.setString(6, reason);
            ps.setLong(7, System.currentTimeMillis());
            if (expiresAt == null) ps.setNull(8, Types.BIGINT);
            else ps.setLong(8, expiresAt);
            ps.setString(9, ip);
            ps.setInt(10, doxxed ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[staff] Failed to add punishment: " + e.getMessage());
        }
        return -1;
    }

    public synchronized void deactivatePunishments(UUID uuid, PunishmentType type) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE uuid = ? AND type = ? AND active = 1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[staff] Failed to deactivate punishments: " + e.getMessage());
        }
    }

    public synchronized void deactivateIpBan(String ip) {
        if (ip == null || ip.isBlank()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ip_bans SET active = 0 WHERE ip = ?")) {
            ps.setString(1, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[staff] Failed to deactivate IP ban: " + e.getMessage());
        }
    }

    /** Clears an IP block from ip_bans and punishments (IP_BAN rows). */
    public synchronized void clearIpBlock(String ip) {
        if (ip == null || ip.isBlank()) return;
        deactivateIpBan(ip);
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE ip = ? AND type = 'IP_BAN' AND active = 1")) {
            ps.setString(1, ip);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to clear IP_BAN punishments: " + e.getMessage());
        }
    }

    /** Removes UUID ban, IP_BAN record, and ip_bans entry for a player. */
    public synchronized void clearAllBansForPlayer(UUID uuid) {
        deactivatePunishments(uuid, PunishmentType.BAN);
        deactivatePunishments(uuid, PunishmentType.IP_BAN);
        for (String ip : ipsForPlayer(uuid)) {
            clearIpBlock(ip);
        }
    }

    public synchronized List<String> ipsForPlayer(UUID uuid) {
        List<String> ips = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT ip FROM player_ips WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ip = rs.getString("ip");
                    if (ip != null && !ip.isBlank()) ips.add(ip);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to list player IPs: " + e.getMessage());
        }
        String latest = latestIp(uuid);
        if (latest != null && !latest.isBlank() && !ips.contains(latest)) ips.add(latest);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT DISTINCT ip FROM punishments WHERE uuid = ? AND ip IS NOT NULL AND ip != ''")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String ip = rs.getString("ip");
                    if (ip != null && !ip.isBlank() && !ips.contains(ip)) ips.add(ip);
                }
            }
        } catch (SQLException ignored) {
        }
        return ips;
    }

    public synchronized void addIpBan(String ip, String reason, String staffName, Long expiresAt) {
        if (ip == null || ip.isBlank()) return;
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO ip_bans (ip, reason, staff_name, created_at, expires_at, active)
                VALUES (?, ?, ?, ?, ?, 1)
                ON CONFLICT(ip) DO UPDATE SET
                    reason = excluded.reason,
                    staff_name = excluded.staff_name,
                    created_at = excluded.created_at,
                    expires_at = excluded.expires_at,
                    active = 1
                """)) {
            ps.setString(1, ip);
            ps.setString(2, reason);
            ps.setString(3, staffName);
            ps.setLong(4, System.currentTimeMillis());
            if (expiresAt == null) ps.setNull(5, Types.BIGINT);
            else ps.setLong(5, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[staff] Failed to add IP ban: " + e.getMessage());
        }
    }

    public synchronized PunishmentRecord getActive(UUID uuid, PunishmentType type) {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT * FROM punishments
                WHERE uuid = ? AND type = ? AND active = 1
                ORDER BY created_at DESC LIMIT 1
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PunishmentRecord record = readPunishment(rs);
                if (record.isExpired()) {
                    expirePunishment(record.id());
                    return null;
                }
                return record;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized PunishmentRecord getActiveIpBan(String ip) {
        if (ip == null || ip.isBlank()) return null;
        PunishmentRecord fromTable = getActiveIpBanFromTable(ip);
        if (fromTable != null) return fromTable;
        return getActiveIpBanFromPunishments(ip);
    }

    private PunishmentRecord getActiveIpBanFromTable(String ip) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM ip_bans WHERE ip = ? AND active = 1")) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                long expiresAt = rs.getLong("expires_at");
                if (!rs.wasNull() && expiresAt > 0 && System.currentTimeMillis() >= expiresAt) {
                    deactivateIpBan(ip);
                    return null;
                }
                return new PunishmentRecord(
                        -1,
                        UUID.randomUUID(),
                        ip,
                        rs.getString("staff_name"),
                        PunishmentType.IP_BAN,
                        rs.getString("reason"),
                        rs.getLong("created_at"),
                        rs.wasNull() ? null : expiresAt,
                        true,
                        ip
                );
            }
        } catch (SQLException e) {
            return null;
        }
    }

    private PunishmentRecord getActiveIpBanFromPunishments(String ip) {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT * FROM punishments
                WHERE ip = ? AND type = 'IP_BAN' AND active = 1
                ORDER BY created_at DESC LIMIT 1
                """)) {
            ps.setString(1, ip);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                PunishmentRecord record = readPunishment(rs);
                if (record.expiresAt() != null && record.expiresAt() > 0 && now >= record.expiresAt()) {
                    expirePunishment(record.id());
                    return null;
                }
                return record;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized boolean hasAnyIpBlock(String ip) {
        return getActiveIpBan(ip) != null;
    }

    public synchronized int countActivePunishments(UUID uuid, PunishmentType type) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM punishments WHERE uuid = ? AND type = ?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized List<PunishmentRecord> history(UUID uuid, PunishmentType type, int limit) {
        List<PunishmentRecord> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT * FROM punishments WHERE uuid = ? AND type = ?
                ORDER BY created_at DESC LIMIT ?
                """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            ps.setInt(3, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(readPunishment(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[staff] Failed to load history: " + e.getMessage());
        }
        return list;
    }

    public synchronized Set<String> knownPlayerNames() {
        Set<String> names = new LinkedHashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT DISTINCT player_name FROM punishments UNION SELECT DISTINCT player_name FROM player_ips")) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && !name.isBlank()) names.add(name);
            }
        } catch (SQLException ignored) {
        }
        return names;
    }

    public synchronized List<String> activePunishedPlayerNames(PunishmentType type) {
        List<String> names = new ArrayList<>();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DISTINCT player_name, expires_at FROM punishments
                WHERE type = ? AND active = 1
                ORDER BY player_name COLLATE NOCASE
                """)) {
            ps.setString(1, type.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long expires = rs.getLong("expires_at");
                    if (!rs.wasNull() && expires > 0 && now >= expires) continue;
                    String name = rs.getString("player_name");
                    if (name != null && !name.isBlank()) names.add(name);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to list active " + type + " names: " + e.getMessage());
        }
        return names;
    }

    public synchronized List<String> activeIpBans() {
        Set<String> ips = new LinkedHashSet<>();
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT ip, expires_at FROM ip_bans WHERE active = 1 ORDER BY ip")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long expires = rs.getLong("expires_at");
                    if (!rs.wasNull() && expires > 0 && now >= expires) continue;
                    String ip = rs.getString("ip");
                    if (ip != null && !ip.isBlank()) ips.add(ip);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to list active IP bans: " + e.getMessage());
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DISTINCT ip, expires_at FROM punishments
                WHERE type = 'IP_BAN' AND active = 1 AND ip IS NOT NULL AND ip != ''
                ORDER BY ip
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long expires = rs.getLong("expires_at");
                    if (!rs.wasNull() && expires > 0 && now >= expires) continue;
                    String ip = rs.getString("ip");
                    if (ip != null && !ip.isBlank()) ips.add(ip);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to list IP_BAN punishments: " + e.getMessage());
        }
        return new ArrayList<>(ips);
    }

    private PunishmentRecord readPunishment(ResultSet rs) throws SQLException {
        long expires = rs.getLong("expires_at");
        return new PunishmentRecord(
                rs.getLong("id"),
                UUID.fromString(rs.getString("uuid")),
                rs.getString("player_name"),
                rs.getString("staff_name"),
                PunishmentType.valueOf(rs.getString("type")),
                rs.getString("reason"),
                rs.getLong("created_at"),
                rs.wasNull() ? null : expires,
                rs.getInt("active") == 1,
                rs.getString("ip")
        );
    }

    private void expirePunishment(long id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public synchronized void close() {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }

    public synchronized int revokeActiveBansExceptDoxxing() {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE type = 'BAN' AND active = 1 AND doxxed = 0")) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to revoke bans: " + e.getMessage());
            return 0;
        }
    }

    public synchronized int revokeActiveMutes() {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE type = 'MUTE' AND active = 1")) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to revoke mutes: " + e.getMessage());
            return 0;
        }
    }

    public synchronized int revokeActiveWarnings() {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE type = 'WARN' AND active = 1")) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to revoke warnings: " + e.getMessage());
            return 0;
        }
    }

    public synchronized int deleteKicks() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM punishments WHERE type = 'KICK'")) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to delete kicks: " + e.getMessage());
            return 0;
        }
    }

    public synchronized int deleteHistory() {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM punishments WHERE active = 0")) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to delete history: " + e.getMessage());
            return 0;
        }
    }

    public synchronized int revokeAllIpBans() {
        int count = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE ip_bans SET active = 0 WHERE active = 1")) {
            count += ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to revoke IP bans: " + e.getMessage());
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE punishments SET active = 0 WHERE type = 'IP_BAN' AND active = 1")) {
            count += ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("[punishments] Failed to revoke IP_BAN punishments: " + e.getMessage());
        }
        return count;
    }
}

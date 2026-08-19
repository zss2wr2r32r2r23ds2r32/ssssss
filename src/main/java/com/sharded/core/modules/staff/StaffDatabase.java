package com.sharded.core.modules.staff;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class StaffDatabase {

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

    public StaffDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "staff.db");
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
                        ip TEXT
                    )
                    """);
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

    public synchronized List<String> findAlts(String ip, UUID exclude) {
        List<String> names = new ArrayList<>();
        if (ip == null || ip.isBlank()) return names;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT DISTINCT player_name FROM player_ips WHERE ip = ? AND uuid != ? ORDER BY last_seen DESC")) {
            ps.setString(1, ip);
            ps.setString(2, exclude.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) names.add(rs.getString("player_name"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[staff] Failed to lookup alts: " + e.getMessage());
        }
        return names;
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
                                           PunishmentType type, String reason, Long expiresAt, String ip) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO punishments (uuid, player_name, staff_uuid, staff_name, type, reason, created_at, expires_at, active, ip)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?)
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
}

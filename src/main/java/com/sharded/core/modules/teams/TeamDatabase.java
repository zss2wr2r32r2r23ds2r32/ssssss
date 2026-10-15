package com.sharded.core.modules.teams;

import com.sharded.core.ShardedCore;

import java.io.File;
import java.sql.*;
import java.util.*;

public final class TeamDatabase {

    public record Team(int id, String name, UUID leaderUuid, long createdAt) {
    }

    public record Member(int teamId, UUID uuid, int role, int kills, long playtimeMs, long joinedAt) {
    }

    public record Invite(int teamId, UUID uuid, UUID invitedBy, long expiresAt) {
    }

    public record AllyRequest(int fromTeamId, int toTeamId, long expiresAt) {
    }

    public record LeaderboardEntry(int teamId, String name, long score, long tokens, int kills, long playtimeMs) {
    }

    public static final int ROLE_LEADER = 0;
    public static final int ROLE_OFFICER = 1;
    public static final int ROLE_MEMBER = 2;

    private final ShardedCore plugin;
    private Connection connection;

    public TeamDatabase(ShardedCore plugin, File folder) throws SQLException {
        this.plugin = plugin;
        File dbFile = new File(folder, "teams.db");
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS teams (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                        leader_uuid TEXT NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS members (
                        team_id INTEGER NOT NULL,
                        uuid TEXT NOT NULL,
                        role INTEGER NOT NULL DEFAULT 2,
                        kills INTEGER NOT NULL DEFAULT 0,
                        playtime_ms INTEGER NOT NULL DEFAULT 0,
                        joined_at INTEGER NOT NULL,
                        PRIMARY KEY (team_id, uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS invites (
                        team_id INTEGER NOT NULL,
                        uuid TEXT NOT NULL,
                        invited_by TEXT NOT NULL,
                        expires_at INTEGER NOT NULL,
                        PRIMARY KEY (team_id, uuid)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ally_requests (
                        from_team_id INTEGER NOT NULL,
                        to_team_id INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL,
                        PRIMARY KEY (from_team_id, to_team_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS allies (
                        team_id INTEGER NOT NULL,
                        ally_team_id INTEGER NOT NULL,
                        PRIMARY KEY (team_id, ally_team_id)
                    )
                    """);
        }
    }

    public synchronized Team createTeam(String name, UUID leader) {
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO teams (name, leader_uuid, created_at) VALUES (?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, leader.toString());
            ps.setLong(3, now);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) return null;
                int id = keys.getInt(1);
                addMember(id, leader, ROLE_LEADER);
                return new Team(id, name, leader, now);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create team: " + e.getMessage());
            return null;
        }
    }

    public synchronized void addMember(int teamId, UUID uuid, int role) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO members (team_id, uuid, role, kills, playtime_ms, joined_at)
                VALUES (?, ?, ?, 0, 0, ?)
                ON CONFLICT(team_id, uuid) DO UPDATE SET role = excluded.role
                """)) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            ps.setInt(3, role);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add team member: " + e.getMessage());
        }
    }

    public synchronized void removeMember(int teamId, UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM members WHERE team_id = ? AND uuid = ?")) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to remove team member: " + e.getMessage());
        }
    }

    public synchronized Team getTeamById(int id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, name, leader_uuid, created_at FROM teams WHERE id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapTeam(rs);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized Team getTeamByName(String name) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT id, name, leader_uuid, created_at FROM teams WHERE name = ? COLLATE NOCASE")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapTeam(rs);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized Integer getTeamId(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT team_id FROM members WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("team_id") : null;
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized Member getMember(int teamId, UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT team_id, uuid, role, kills, playtime_ms, joined_at FROM members WHERE team_id = ? AND uuid = ?")) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapMember(rs);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized List<Member> getMembers(int teamId) {
        List<Member> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT team_id, uuid, role, kills, playtime_ms, joined_at FROM members WHERE team_id = ? ORDER BY role ASC, joined_at ASC")) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapMember(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to list members: " + e.getMessage());
        }
        return list;
    }

    public synchronized void setRole(int teamId, UUID uuid, int role) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET role = ? WHERE team_id = ? AND uuid = ?")) {
            ps.setInt(1, role);
            ps.setInt(2, teamId);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to set role: " + e.getMessage());
        }
    }

    public synchronized void deleteTeam(int teamId) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM invites WHERE team_id = " + teamId);
            statement.executeUpdate("DELETE FROM members WHERE team_id = " + teamId);
            statement.executeUpdate("DELETE FROM allies WHERE team_id = " + teamId + " OR ally_team_id = " + teamId);
            statement.executeUpdate("DELETE FROM ally_requests WHERE from_team_id = " + teamId + " OR to_team_id = " + teamId);
            statement.executeUpdate("DELETE FROM teams WHERE id = " + teamId);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to delete team: " + e.getMessage());
        }
    }

    public synchronized void addInvite(int teamId, UUID uuid, UUID invitedBy, long expiresAt) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO invites (team_id, uuid, invited_by, expires_at) VALUES (?, ?, ?, ?)
                ON CONFLICT(team_id, uuid) DO UPDATE SET invited_by = excluded.invited_by, expires_at = excluded.expires_at
                """)) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            ps.setString(3, invitedBy.toString());
            ps.setLong(4, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add invite: " + e.getMessage());
        }
    }

    public synchronized Invite getInvite(UUID uuid, int teamId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT team_id, uuid, invited_by, expires_at FROM invites WHERE uuid = ? AND team_id = ?")) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Invite(rs.getInt("team_id"), UUID.fromString(rs.getString("uuid")),
                        UUID.fromString(rs.getString("invited_by")), rs.getLong("expires_at"));
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized Invite getLatestInvite(UUID uuid) {
        purgeExpiredInvites();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT team_id, uuid, invited_by, expires_at FROM invites WHERE uuid = ? ORDER BY expires_at DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Invite(rs.getInt("team_id"), UUID.fromString(rs.getString("uuid")),
                        UUID.fromString(rs.getString("invited_by")), rs.getLong("expires_at"));
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized void removeInvite(int teamId, UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM invites WHERE team_id = ? AND uuid = ?")) {
            ps.setInt(1, teamId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to remove invite: " + e.getMessage());
        }
    }

    public synchronized void purgeExpiredInvites() {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM invites WHERE expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public synchronized void incrementKills(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET kills = kills + 1 WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to increment kills: " + e.getMessage());
        }
    }

    public synchronized void addPlaytime(UUID uuid, long ms) {
        if (ms <= 0) return;
        try (PreparedStatement ps = connection.prepareStatement("UPDATE members SET playtime_ms = playtime_ms + ? WHERE uuid = ?")) {
            ps.setLong(1, ms);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add playtime: " + e.getMessage());
        }
    }

    public synchronized int allyCount(int teamId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) AS c FROM allies WHERE team_id = ?")) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt("c") : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public synchronized List<Integer> getAllies(int teamId) {
        List<Integer> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT ally_team_id FROM allies WHERE team_id = ?")) {
            ps.setInt(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(rs.getInt("ally_team_id"));
            }
        } catch (SQLException ignored) {
        }
        return list;
    }

    public synchronized void addAllyPair(int teamA, int teamB) {
        insertAlly(teamA, teamB);
        insertAlly(teamB, teamA);
    }

    private void insertAlly(int teamId, int allyTeamId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO allies (team_id, ally_team_id) VALUES (?, ?)")) {
            ps.setInt(1, teamId);
            ps.setInt(2, allyTeamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add ally: " + e.getMessage());
        }
    }

    public synchronized void removeAllyPair(int teamA, int teamB) {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM allies WHERE (team_id = " + teamA + " AND ally_team_id = " + teamB + ")");
            statement.executeUpdate("DELETE FROM allies WHERE (team_id = " + teamB + " AND ally_team_id = " + teamA + ")");
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to remove ally: " + e.getMessage());
        }
    }

    public synchronized void addAllyRequest(int fromTeamId, int toTeamId, long expiresAt) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO ally_requests (from_team_id, to_team_id, expires_at) VALUES (?, ?, ?)
                ON CONFLICT(from_team_id, to_team_id) DO UPDATE SET expires_at = excluded.expires_at
                """)) {
            ps.setInt(1, fromTeamId);
            ps.setInt(2, toTeamId);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to add ally request: " + e.getMessage());
        }
    }

    public synchronized AllyRequest getIncomingAllyRequest(int toTeamId) {
        purgeExpiredAllyRequests();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT from_team_id, to_team_id, expires_at FROM ally_requests WHERE to_team_id = ? ORDER BY expires_at DESC LIMIT 1")) {
            ps.setInt(1, toTeamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new AllyRequest(rs.getInt("from_team_id"), rs.getInt("to_team_id"), rs.getLong("expires_at"));
            }
        } catch (SQLException e) {
            return null;
        }
    }

    public synchronized void removeAllyRequest(int fromTeamId, int toTeamId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM ally_requests WHERE from_team_id = ? AND to_team_id = ?")) {
            ps.setInt(1, fromTeamId);
            ps.setInt(2, toTeamId);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void purgeExpiredAllyRequests() {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM ally_requests WHERE expires_at < ?")) {
            ps.setLong(1, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public synchronized List<Team> listTeams() {
        List<Team> list = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id, name, leader_uuid, created_at FROM teams ORDER BY name ASC")) {
            while (rs.next()) list.add(mapTeam(rs));
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to list teams: " + e.getMessage());
        }
        return list;
    }

    public synchronized void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException ignored) {
        }
        connection = null;
    }

    private Team mapTeam(ResultSet rs) throws SQLException {
        return new Team(rs.getInt("id"), rs.getString("name"),
                UUID.fromString(rs.getString("leader_uuid")), rs.getLong("created_at"));
    }

    private Member mapMember(ResultSet rs) throws SQLException {
        return new Member(rs.getInt("team_id"), UUID.fromString(rs.getString("uuid")),
                rs.getInt("role"), rs.getInt("kills"), rs.getLong("playtime_ms"), rs.getLong("joined_at"));
    }
}

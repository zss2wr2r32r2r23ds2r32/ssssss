package com.sharded.core.modules.teams;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.crates.CrateStorage;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

public final class TeamDatabase {
   public static final int ROLE_LEADER = 0;
   public static final int ROLE_OFFICER = 1;
   public static final int ROLE_MEMBER = 2;
   private final ShardedCore plugin;
   private Connection connection;

   public TeamDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      if (!folder.exists() && !folder.mkdirs()) {
         throw new SQLException("Could not create teams folder: " + folder);
      } else {
         File file1 = new File(folder, "teams.db");
         this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

         try (Statement statement = this.connection.createStatement()) {
            statement.execute(
               "CREATE TABLE IF NOT EXISTS teams (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    name TEXT NOT NULL UNIQUE COLLATE NOCASE,\n    leader_uuid TEXT NOT NULL,\n    created_at INTEGER NOT NULL\n)\n"
            );
            statement.execute(
               "CREATE TABLE IF NOT EXISTS members (\n    team_id INTEGER NOT NULL,\n    uuid TEXT NOT NULL,\n    role INTEGER NOT NULL DEFAULT 2,\n    kills INTEGER NOT NULL DEFAULT 0,\n    playtime_ms INTEGER NOT NULL DEFAULT 0,\n    joined_at INTEGER NOT NULL,\n    PRIMARY KEY (team_id, uuid)\n)\n"
            );
            statement.execute(
               "CREATE TABLE IF NOT EXISTS invites (\n    team_id INTEGER NOT NULL,\n    uuid TEXT NOT NULL,\n    invited_by TEXT NOT NULL,\n    expires_at INTEGER NOT NULL,\n    PRIMARY KEY (team_id, uuid)\n)\n"
            );
            statement.execute(
               "CREATE TABLE IF NOT EXISTS ally_requests (\n    from_team_id INTEGER NOT NULL,\n    to_team_id INTEGER NOT NULL,\n    expires_at INTEGER NOT NULL,\n    PRIMARY KEY (from_team_id, to_team_id)\n)\n"
            );
            statement.execute(
               "CREATE TABLE IF NOT EXISTS allies (\n    team_id INTEGER NOT NULL,\n    ally_team_id INTEGER NOT NULL,\n    PRIMARY KEY (team_id, ally_team_id)\n)\n"
            );
            statement.execute(
               "CREATE TABLE IF NOT EXISTS team_enderchest (\n    team_id INTEGER NOT NULL,\n    slot INTEGER NOT NULL,\n    item_base64 TEXT,\n    PRIMARY KEY (team_id, slot)\n)\n"
            );
            this.migrateTeamsColumns(statement);
         }
      }
   }

   private void migrateTeamsColumns(Statement statement) {
      this.addColumnIfMissing(statement, "teams", "home_world", "TEXT");
      this.addColumnIfMissing(statement, "teams", "home_x", "REAL");
      this.addColumnIfMissing(statement, "teams", "home_y", "REAL");
      this.addColumnIfMissing(statement, "teams", "home_z", "REAL");
      this.addColumnIfMissing(statement, "teams", "home_yaw", "REAL");
      this.addColumnIfMissing(statement, "teams", "home_pitch", "REAL");
      this.addColumnIfMissing(statement, "teams", "pvp_enabled", "INTEGER DEFAULT 0");
      this.addColumnIfMissing(statement, "teams", "display", "TEXT");
   }

   private void addColumnIfMissing(Statement statement, String table, String column, String definition) {
      try {
         statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
      } catch (SQLException sqlexception) {
      }
   }

   public synchronized TeamDatabase.Team createTeam(String name, UUID leader) {
      long i = System.currentTimeMillis();

      try {
         TeamDatabase.Team teamdatabase$team;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("INSERT INTO teams (name, leader_uuid, created_at, display, pvp_enabled) VALUES (?, ?, ?, ?, 0)", 1)) {
            preparedstatement.setString(1, name);
            preparedstatement.setString(2, leader.toString());
            preparedstatement.setLong(3, i);
            preparedstatement.setString(4, name);
            preparedstatement.executeUpdate();

            try (ResultSet resultset = preparedstatement.getGeneratedKeys()) {
               if (!resultset.next()) {
                  return null;
               }

               int j = resultset.getInt(1);
               this.addMember(j, leader, 0);
               teamdatabase$team = new TeamDatabase.Team(j, name, leader, i, name, false);
            }
         }

         return teamdatabase$team;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to create team: " + sqlexception.getMessage());
         return null;
      }
   }

   public synchronized void addMember(int teamId, UUID uuid, int role) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO members (team_id, uuid, role, kills, playtime_ms, joined_at)\nVALUES (?, ?, ?, 0, 0, ?)\nON CONFLICT(team_id, uuid) DO UPDATE SET role = excluded.role\n"
            )) {
         preparedstatement.setInt(1, teamId);
         preparedstatement.setString(2, uuid.toString());
         preparedstatement.setInt(3, role);
         preparedstatement.setLong(4, System.currentTimeMillis());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to add team member: " + sqlexception.getMessage());
      }
   }

   public synchronized void removeMember(int teamId, UUID uuid) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM members WHERE team_id = ? AND uuid = ?")) {
         preparedstatement.setInt(1, teamId);
         preparedstatement.setString(2, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to remove team member: " + sqlexception.getMessage());
      }
   }

   public synchronized TeamDatabase.Team getTeamById(int id) {
      try {
         TeamDatabase.Team teamdatabase$team;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT id, name, leader_uuid, created_at, display, pvp_enabled FROM teams WHERE id = ?")) {
            preparedstatement.setInt(1, id);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               teamdatabase$team = resultset.next() ? this.mapTeam(resultset) : null;
            }
         }

         return teamdatabase$team;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized TeamDatabase.Team getTeamByName(String name) {
      try {
         TeamDatabase.Team teamdatabase$team;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT id, name, leader_uuid, created_at, display, pvp_enabled FROM teams WHERE name = ? COLLATE NOCASE")) {
            preparedstatement.setString(1, name);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               teamdatabase$team = resultset.next() ? this.mapTeam(resultset) : null;
            }
         }

         return teamdatabase$team;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized Integer getTeamId(UUID uuid) {
      try {
         Integer integer;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT team_id FROM members WHERE uuid = ?")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               integer = resultset.next() ? resultset.getInt("team_id") : null;
            }
         }

         return integer;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized TeamDatabase.Member getMember(int teamId, UUID uuid) {
      try {
         TeamDatabase.Member teamdatabase$member;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT team_id, uuid, role, kills, playtime_ms, joined_at FROM members WHERE team_id = ? AND uuid = ?")) {
            preparedstatement.setInt(1, teamId);
            preparedstatement.setString(2, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               teamdatabase$member = resultset.next() ? this.mapMember(resultset) : null;
            }
         }

         return teamdatabase$member;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized List<TeamDatabase.Member> getMembers(int teamId) {
      ArrayList<TeamDatabase.Member> arraylist = new ArrayList<>();

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("SELECT team_id, uuid, role, kills, playtime_ms, joined_at FROM members WHERE team_id = ? ORDER BY role ASC, joined_at ASC")) {
         preparedstatement.setInt(1, teamId);

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               arraylist.add(this.mapMember(resultset));
            }
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to list members: " + sqlexception.getMessage());
      }

      return arraylist;
   }

   public synchronized void setRole(int teamId, UUID uuid, int role) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE members SET role = ? WHERE team_id = ? AND uuid = ?")) {
         preparedstatement.setInt(1, role);
         preparedstatement.setInt(2, teamId);
         preparedstatement.setString(3, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to set role: " + sqlexception.getMessage());
      }
   }

   public synchronized void deleteTeam(int teamId) {
      try (Statement statement = this.connection.createStatement()) {
         statement.executeUpdate("DELETE FROM invites WHERE team_id = " + teamId);
         statement.executeUpdate("DELETE FROM members WHERE team_id = " + teamId);
         statement.executeUpdate("DELETE FROM allies WHERE team_id = " + teamId + " OR ally_team_id = " + teamId);
         statement.executeUpdate("DELETE FROM ally_requests WHERE from_team_id = " + teamId + " OR to_team_id = " + teamId);
         statement.executeUpdate("DELETE FROM team_enderchest WHERE team_id = " + teamId);
         statement.executeUpdate("DELETE FROM teams WHERE id = " + teamId);
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to delete team: " + sqlexception.getMessage());
      }
   }

   public synchronized void setHome(int teamId, Location location) {
      if (location != null && location.getWorld() != null) {
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("UPDATE teams SET home_world = ?, home_x = ?, home_y = ?, home_z = ?, home_yaw = ?, home_pitch = ?\nWHERE id = ?\n")) {
            preparedstatement.setString(1, location.getWorld().getName());
            preparedstatement.setDouble(2, location.getX());
            preparedstatement.setDouble(3, location.getY());
            preparedstatement.setDouble(4, location.getZ());
            preparedstatement.setFloat(5, location.getYaw());
            preparedstatement.setFloat(6, location.getPitch());
            preparedstatement.setInt(7, teamId);
            preparedstatement.executeUpdate();
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("Failed to set team home: " + sqlexception.getMessage());
         }
      }
   }

   public synchronized Location getHome(int teamId) {
      try {
         Object object;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT home_world, home_x, home_y, home_z, home_yaw, home_pitch FROM teams WHERE id = ?")) {
            preparedstatement.setInt(1, teamId);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               String s = resultset.getString("home_world");
               if (s != null && !s.isBlank()) {
                  World world = Bukkit.getWorld(s);
                  if (world == null) {
                     return null;
                  }

                  double d0 = resultset.getDouble("home_x");
                  if (resultset.wasNull()) {
                     return null;
                  }

                  return new Location(
                     world, d0, resultset.getDouble("home_y"), resultset.getDouble("home_z"), resultset.getFloat("home_yaw"), resultset.getFloat("home_pitch")
                  );
               }

               object = null;
            }
         }

         return (Location)object;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized void clearHome(int teamId) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "UPDATE teams SET home_world = NULL, home_x = NULL, home_y = NULL, home_z = NULL,\nhome_yaw = NULL, home_pitch = NULL WHERE id = ?\n"
            )) {
         preparedstatement.setInt(1, teamId);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to clear team home: " + sqlexception.getMessage());
      }
   }

   public synchronized void setPvp(int teamId, boolean enabled) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE teams SET pvp_enabled = ? WHERE id = ?")) {
         preparedstatement.setInt(1, enabled ? 1 : 0);
         preparedstatement.setInt(2, teamId);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to set team pvp: " + sqlexception.getMessage());
      }
   }

   public synchronized boolean isPvp(int teamId) {
      try {
         boolean flag;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT pvp_enabled FROM teams WHERE id = ?")) {
            preparedstatement.setInt(1, teamId);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               flag = resultset.next() && resultset.getInt("pvp_enabled") != 0;
            }
         }

         return flag;
      } catch (SQLException sqlexception) {
         return false;
      }
   }

   public synchronized boolean renameTeam(int teamId, String newName) {
      try {
         boolean flag;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE teams SET name = ?, display = ? WHERE id = ?")) {
            preparedstatement.setString(1, newName);
            preparedstatement.setString(2, newName);
            preparedstatement.setInt(3, teamId);
            flag = preparedstatement.executeUpdate() > 0;
         }

         return flag;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to rename team: " + sqlexception.getMessage());
         return false;
      }
   }

   public synchronized String getDisplay(int teamId) {
      TeamDatabase.Team teamdatabase$team = this.getTeamById(teamId);
      if (teamdatabase$team == null) {
         return null;
      } else {
         return teamdatabase$team.display() != null && !teamdatabase$team.display().isBlank() ? teamdatabase$team.display() : teamdatabase$team.name();
      }
   }

   public synchronized void setDisplay(int teamId, String display) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE teams SET display = ? WHERE id = ?")) {
         preparedstatement.setString(1, display);
         preparedstatement.setInt(2, teamId);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to set team display: " + sqlexception.getMessage());
      }
   }

   public synchronized ItemStack[] getEnderchest(int teamId) {
      ItemStack[] aitemstack = new ItemStack[27];

      try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT slot, item_base64 FROM team_enderchest WHERE team_id = ?")) {
         preparedstatement.setInt(1, teamId);

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               int i = resultset.getInt("slot");
               if (i >= 0 && i < aitemstack.length) {
                  String s = resultset.getString("item_base64");
                  if (s != null && !s.isBlank()) {
                     aitemstack[i] = CrateStorage.deserializeItem(s);
                  }
               }
            }
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to load team enderchest: " + sqlexception.getMessage());
      }

      return aitemstack;
   }

   public synchronized void setEnderchest(int teamId, ItemStack[] contents) {
      try {
         this.connection.setAutoCommit(false);

         try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM team_enderchest WHERE team_id = ?")) {
            preparedstatement.setInt(1, teamId);
            preparedstatement.executeUpdate();
         }

         if (contents != null) {
            try (PreparedStatement preparedstatement1 = this.connection
                  .prepareStatement("INSERT INTO team_enderchest (team_id, slot, item_base64) VALUES (?, ?, ?)")) {
               int i = Math.min(27, contents.length);

               for (int j = 0; j < i; j++) {
                  ItemStack itemstack = contents[j];
                  if (itemstack != null && !itemstack.getType().isAir()) {
                     preparedstatement1.setInt(1, teamId);
                     preparedstatement1.setInt(2, j);
                     preparedstatement1.setString(3, CrateStorage.serializeItem(itemstack));
                     preparedstatement1.addBatch();
                  }
               }

               preparedstatement1.executeBatch();
            }
         }

         this.connection.commit();
      } catch (SQLException sqlexception2) {
         try {
            this.connection.rollback();
         } catch (SQLException sqlexception1) {
         }

         this.plugin.getLogger().warning("Failed to save team enderchest: " + sqlexception2.getMessage());
      } finally {
         try {
            this.connection.setAutoCommit(true);
         } catch (SQLException sqlexception) {
         }
      }
   }

   public synchronized void addInvite(int teamId, UUID uuid, UUID invitedBy, long expiresAt) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO invites (team_id, uuid, invited_by, expires_at) VALUES (?, ?, ?, ?)\nON CONFLICT(team_id, uuid) DO UPDATE SET invited_by = excluded.invited_by, expires_at = excluded.expires_at\n"
            )) {
         preparedstatement.setInt(1, teamId);
         preparedstatement.setString(2, uuid.toString());
         preparedstatement.setString(3, invitedBy.toString());
         preparedstatement.setLong(4, expiresAt);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to add invite: " + sqlexception.getMessage());
      }
   }

   public synchronized TeamDatabase.Invite getInvite(UUID uuid, int teamId) {
      try {
         TeamDatabase.Invite teamdatabase$invite;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT team_id, uuid, invited_by, expires_at FROM invites WHERE uuid = ? AND team_id = ?")) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setInt(2, teamId);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               teamdatabase$invite = new TeamDatabase.Invite(
                  resultset.getInt("team_id"),
                  UUID.fromString(resultset.getString("uuid")),
                  UUID.fromString(resultset.getString("invited_by")),
                  resultset.getLong("expires_at")
               );
            }
         }

         return teamdatabase$invite;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized TeamDatabase.Invite getLatestInvite(UUID uuid) {
      this.purgeExpiredInvites();

      try {
         TeamDatabase.Invite teamdatabase$invite;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT team_id, uuid, invited_by, expires_at FROM invites WHERE uuid = ? ORDER BY expires_at DESC LIMIT 1")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               teamdatabase$invite = new TeamDatabase.Invite(
                  resultset.getInt("team_id"),
                  UUID.fromString(resultset.getString("uuid")),
                  UUID.fromString(resultset.getString("invited_by")),
                  resultset.getLong("expires_at")
               );
            }
         }

         return teamdatabase$invite;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized void removeInvite(int teamId, UUID uuid) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM invites WHERE team_id = ? AND uuid = ?")) {
         preparedstatement.setInt(1, teamId);
         preparedstatement.setString(2, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to remove invite: " + sqlexception.getMessage());
      }
   }

   public synchronized void purgeExpiredInvites() {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM invites WHERE expires_at < ?")) {
         preparedstatement.setLong(1, System.currentTimeMillis());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
      }
   }

   public synchronized void incrementKills(UUID uuid) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE members SET kills = kills + 1 WHERE uuid = ?")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to increment kills: " + sqlexception.getMessage());
      }
   }

   public synchronized void addPlaytime(UUID uuid, long ms) {
      if (ms > 0L) {
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE members SET playtime_ms = playtime_ms + ? WHERE uuid = ?")) {
            preparedstatement.setLong(1, ms);
            preparedstatement.setString(2, uuid.toString());
            preparedstatement.executeUpdate();
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("Failed to add playtime: " + sqlexception.getMessage());
         }
      }
   }

   public synchronized int allyCount(int teamId) {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT COUNT(*) AS c FROM allies WHERE team_id = ?")) {
            preparedstatement.setInt(1, teamId);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               i = resultset.next() ? resultset.getInt("c") : 0;
            }
         }

         return i;
      } catch (SQLException sqlexception) {
         return 0;
      }
   }

   public synchronized List<Integer> getAllies(int teamId) {
      ArrayList<Integer> arraylist = new ArrayList<>();

      try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT ally_team_id FROM allies WHERE team_id = ?")) {
         preparedstatement.setInt(1, teamId);

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               arraylist.add(resultset.getInt("ally_team_id"));
            }
         }
      } catch (SQLException sqlexception) {
      }

      return arraylist;
   }

   public synchronized void addAllyPair(int teamA, int teamB) {
      this.insertAlly(teamA, teamB);
      this.insertAlly(teamB, teamA);
   }

   private void insertAlly(int teamId, int allyTeamId) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("INSERT OR IGNORE INTO allies (team_id, ally_team_id) VALUES (?, ?)")) {
         preparedstatement.setInt(1, teamId);
         preparedstatement.setInt(2, allyTeamId);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to add ally: " + sqlexception.getMessage());
      }
   }

   public synchronized void removeAllyPair(int teamA, int teamB) {
      try (Statement statement = this.connection.createStatement()) {
         statement.executeUpdate("DELETE FROM allies WHERE (team_id = " + teamA + " AND ally_team_id = " + teamB + ")");
         statement.executeUpdate("DELETE FROM allies WHERE (team_id = " + teamB + " AND ally_team_id = " + teamA + ")");
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to remove ally: " + sqlexception.getMessage());
      }
   }

   public synchronized void addAllyRequest(int fromTeamId, int toTeamId, long expiresAt) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "INSERT INTO ally_requests (from_team_id, to_team_id, expires_at) VALUES (?, ?, ?)\nON CONFLICT(from_team_id, to_team_id) DO UPDATE SET expires_at = excluded.expires_at\n"
            )) {
         preparedstatement.setInt(1, fromTeamId);
         preparedstatement.setInt(2, toTeamId);
         preparedstatement.setLong(3, expiresAt);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to add ally request: " + sqlexception.getMessage());
      }
   }

   public synchronized TeamDatabase.AllyRequest getIncomingAllyRequest(int toTeamId) {
      this.purgeExpiredAllyRequests();

      try {
         TeamDatabase.AllyRequest teamdatabase$allyrequest;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT from_team_id, to_team_id, expires_at FROM ally_requests WHERE to_team_id = ? ORDER BY expires_at DESC LIMIT 1")) {
            preparedstatement.setInt(1, toTeamId);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               teamdatabase$allyrequest = new TeamDatabase.AllyRequest(
                  resultset.getInt("from_team_id"), resultset.getInt("to_team_id"), resultset.getLong("expires_at")
               );
            }
         }

         return teamdatabase$allyrequest;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized void removeAllyRequest(int fromTeamId, int toTeamId) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM ally_requests WHERE from_team_id = ? AND to_team_id = ?")) {
         preparedstatement.setInt(1, fromTeamId);
         preparedstatement.setInt(2, toTeamId);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
      }
   }

   private void purgeExpiredAllyRequests() {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM ally_requests WHERE expires_at < ?")) {
         preparedstatement.setLong(1, System.currentTimeMillis());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
      }
   }

   public synchronized List<TeamDatabase.Team> listTeams() {
      ArrayList<TeamDatabase.Team> arraylist = new ArrayList<>();

      try (
         Statement statement = this.connection.createStatement();
         ResultSet resultset = statement.executeQuery("SELECT id, name, leader_uuid, created_at, display, pvp_enabled FROM teams ORDER BY name ASC");
      ) {
         while (resultset.next()) {
            arraylist.add(this.mapTeam(resultset));
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to list teams: " + sqlexception.getMessage());
      }

      return arraylist;
   }

   public synchronized void close() {
      try {
         if (this.connection != null && !this.connection.isClosed()) {
            this.connection.close();
         }
      } catch (SQLException sqlexception) {
      }

      this.connection = null;
   }

   private TeamDatabase.Team mapTeam(ResultSet rs) throws SQLException {
      String s = rs.getString("display");
      String s1 = rs.getString("name");
      if (s == null || s.isBlank()) {
         s = s1;
      }

      boolean flag = false;

      try {
         flag = rs.getInt("pvp_enabled") != 0;
      } catch (SQLException sqlexception) {
      }

      return new TeamDatabase.Team(rs.getInt("id"), s1, UUID.fromString(rs.getString("leader_uuid")), rs.getLong("created_at"), s, flag);
   }

   private TeamDatabase.Member mapMember(ResultSet rs) throws SQLException {
      return new TeamDatabase.Member(
         rs.getInt("team_id"), UUID.fromString(rs.getString("uuid")), rs.getInt("role"), rs.getInt("kills"), rs.getLong("playtime_ms"), rs.getLong("joined_at")
      );
   }

   public static record AllyRequest(int fromTeamId, int toTeamId, long expiresAt) {
   }

   public static record Invite(int teamId, UUID uuid, UUID invitedBy, long expiresAt) {
   }

   public static record LeaderboardEntry(int teamId, String name, long score, long tokens, int kills, long playtimeMs) {
   }

   public static record Member(int teamId, UUID uuid, int role, int kills, long playtimeMs, long joinedAt) {
   }

   public static record Team(int id, String name, UUID leaderUuid, long createdAt, String display, boolean pvpEnabled) {
   }
}

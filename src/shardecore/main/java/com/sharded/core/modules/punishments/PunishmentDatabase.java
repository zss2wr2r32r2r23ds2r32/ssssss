package com.sharded.core.modules.punishments;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PunishmentDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public PunishmentDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file1 = new File(folder, "punishments.db");
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

      try (Statement statement = this.connection.createStatement()) {
         statement.execute("PRAGMA journal_mode=WAL");
         statement.execute(
            "CREATE TABLE IF NOT EXISTS punishments (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    uuid TEXT NOT NULL,\n    player_name TEXT NOT NULL,\n    staff_uuid TEXT,\n    staff_name TEXT NOT NULL,\n    type TEXT NOT NULL,\n    reason TEXT NOT NULL,\n    created_at INTEGER NOT NULL,\n    expires_at INTEGER,\n    active INTEGER NOT NULL DEFAULT 1,\n    ip TEXT,\n    doxxed INTEGER NOT NULL DEFAULT 0\n)\n"
         );

         try {
            statement.execute("ALTER TABLE punishments ADD COLUMN doxxed INTEGER NOT NULL DEFAULT 0");
         } catch (SQLException sqlexception) {
         }

         statement.execute(
            "CREATE TABLE IF NOT EXISTS player_ips (\n    uuid TEXT NOT NULL,\n    player_name TEXT NOT NULL,\n    ip TEXT NOT NULL,\n    last_seen INTEGER NOT NULL,\n    PRIMARY KEY (uuid, ip)\n)\n"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS ip_bans (\n    ip TEXT PRIMARY KEY,\n    reason TEXT NOT NULL,\n    staff_name TEXT NOT NULL,\n    created_at INTEGER NOT NULL,\n    expires_at INTEGER,\n    active INTEGER NOT NULL DEFAULT 1\n)\n"
         );
         statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_uuid ON punishments(uuid)");
         statement.execute("CREATE INDEX IF NOT EXISTS idx_punishments_ip ON punishments(ip)");
         statement.execute("CREATE INDEX IF NOT EXISTS idx_player_ips_ip ON player_ips(ip)");
      }
   }

   public synchronized void recordIp(UUID uuid, String playerName, String ip) {
      if (ip != null && !ip.isBlank()) {
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement(
                  "INSERT INTO player_ips (uuid, player_name, ip, last_seen) VALUES (?, ?, ?, ?)\nON CONFLICT(uuid, ip) DO UPDATE SET\n    player_name = excluded.player_name,\n    last_seen = excluded.last_seen\n"
               )) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setString(2, playerName);
            preparedstatement.setString(3, ip);
            preparedstatement.setLong(4, System.currentTimeMillis());
            preparedstatement.executeUpdate();
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("[staff] Failed to record IP: " + sqlexception.getMessage());
         }
      }
   }

   public synchronized List<PunishmentDatabase.AltAccount> findAlts(String ip, UUID exclude) {
      List<PunishmentDatabase.AltAccount> list = new ArrayList<>();
      if (ip != null && !ip.isBlank()) {
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT uuid, player_name FROM player_ips WHERE ip = ? AND uuid != ? ORDER BY last_seen DESC")) {
            preparedstatement.setString(1, ip);
            preparedstatement.setString(2, exclude.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               while (resultset.next()) {
                  list.add(new PunishmentDatabase.AltAccount(resultset.getString("player_name"), UUID.fromString(resultset.getString("uuid"))));
               }
            }
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("[punishments] Failed to lookup alts: " + sqlexception.getMessage());
         }

         return list;
      } else {
         return list;
      }
   }

   public synchronized boolean isDoxxed(UUID uuid) {
      try {
         boolean flag;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT 1 FROM punishments WHERE uuid = ? AND doxxed = 1 AND active = 1 LIMIT 1")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               flag = resultset.next();
            }
         }

         return flag;
      } catch (SQLException sqlexception) {
         return false;
      }
   }

   public synchronized boolean hasBannedAltOnIp(String ip, UUID joiningUuid) {
      if (ip != null && !ip.isBlank()) {
         try {
            boolean flag;
            try (PreparedStatement preparedstatement = this.connection
                  .prepareStatement(
                     "SELECT 1 FROM player_ips pi\nJOIN punishments p ON p.uuid = pi.uuid\nWHERE pi.ip = ? AND pi.uuid != ? AND p.type = 'BAN' AND p.active = 1\nLIMIT 1\n"
                  )) {
               preparedstatement.setString(1, ip);
               preparedstatement.setString(2, joiningUuid.toString());

               try (ResultSet resultset = preparedstatement.executeQuery()) {
                  flag = resultset.next();
               }
            }

            return flag;
         } catch (SQLException sqlexception) {
            return false;
         }
      } else {
         return false;
      }
   }

   public synchronized void markDoxxed(UUID uuid) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("UPDATE punishments SET doxxed = 1 WHERE uuid = ? AND type = 'BAN' AND active = 1")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
      }
   }

   public synchronized String latestIp(UUID uuid) {
      try {
         String s;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT ip FROM player_ips WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1")) {
            preparedstatement.setString(1, uuid.toString());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               s = resultset.next() ? resultset.getString("ip") : null;
            }
         }

         return s;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized long addPunishment(
      UUID uuid,
      String playerName,
      UUID staffUuid,
      String staffName,
      PunishmentDatabase.PunishmentType type,
      String reason,
      Long expiresAt,
      String ip,
      boolean doxxed
   ) {
      try {
         long i;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement(
                  "INSERT INTO punishments (uuid, player_name, staff_uuid, staff_name, type, reason, created_at, expires_at, active, ip, doxxed)\nVALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)\n",
                  1
               )) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setString(2, playerName);
            preparedstatement.setString(3, staffUuid == null ? null : staffUuid.toString());
            preparedstatement.setString(4, staffName);
            preparedstatement.setString(5, type.name());
            preparedstatement.setString(6, reason);
            preparedstatement.setLong(7, System.currentTimeMillis());
            if (expiresAt == null) {
               preparedstatement.setNull(8, -5);
            } else {
               preparedstatement.setLong(8, expiresAt);
            }

            preparedstatement.setString(9, ip);
            preparedstatement.setInt(10, doxxed ? 1 : 0);
            preparedstatement.executeUpdate();

            try (ResultSet resultset = preparedstatement.getGeneratedKeys()) {
               if (!resultset.next()) {
                  return -1L;
               }

               i = resultset.getLong(1);
            }
         }

         return i;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[staff] Failed to add punishment: " + sqlexception.getMessage());
         return -1L;
      }
   }

   public synchronized void deactivatePunishments(UUID uuid, PunishmentDatabase.PunishmentType type) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("UPDATE punishments SET active = 0 WHERE uuid = ? AND type = ? AND active = 1")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, type.name());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[staff] Failed to deactivate punishments: " + sqlexception.getMessage());
      }
   }

   public synchronized void deactivateIpBan(String ip) {
      if (ip != null && !ip.isBlank()) {
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE ip_bans SET active = 0 WHERE ip = ?")) {
            preparedstatement.setString(1, ip);
            preparedstatement.executeUpdate();
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("[staff] Failed to deactivate IP ban: " + sqlexception.getMessage());
         }
      }
   }

   public synchronized void clearIpBlock(String ip) {
      if (ip != null && !ip.isBlank()) {
         this.deactivateIpBan(ip);

         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("UPDATE punishments SET active = 0 WHERE ip = ? AND type = 'IP_BAN' AND active = 1")) {
            preparedstatement.setString(1, ip);
            preparedstatement.executeUpdate();
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("[punishments] Failed to clear IP_BAN punishments: " + sqlexception.getMessage());
         }
      }
   }

   public synchronized void clearAllBansForPlayer(UUID uuid) {
      this.deactivatePunishments(uuid, PunishmentDatabase.PunishmentType.BAN);
      this.deactivatePunishments(uuid, PunishmentDatabase.PunishmentType.IP_BAN);

      for (String s : this.ipsForPlayer(uuid)) {
         this.clearIpBlock(s);
      }
   }

   public synchronized List<String> ipsForPlayer(UUID uuid) {
      List<String> list = new ArrayList<>();

      try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT ip FROM player_ips WHERE uuid = ?")) {
         preparedstatement.setString(1, uuid.toString());

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               String s = resultset.getString("ip");
               if (s != null && !s.isBlank()) {
                  list.add(s);
               }
            }
         }
      } catch (SQLException sqlexception1) {
         this.plugin.getLogger().warning("[punishments] Failed to list player IPs: " + sqlexception1.getMessage());
      }

      String s2 = this.latestIp(uuid);
      if (s2 != null && !s2.isBlank() && !list.contains(s2)) {
         list.add(s2);
      }

      try (PreparedStatement preparedstatement1 = this.connection
            .prepareStatement("SELECT DISTINCT ip FROM punishments WHERE uuid = ? AND ip IS NOT NULL AND ip != ''")) {
         preparedstatement1.setString(1, uuid.toString());

         try (ResultSet resultset1 = preparedstatement1.executeQuery()) {
            while (resultset1.next()) {
               String s1 = resultset1.getString("ip");
               if (s1 != null && !s1.isBlank() && !list.contains(s1)) {
                  list.add(s1);
               }
            }
         }
      } catch (SQLException sqlexception) {
      }

      return list;
   }

   public synchronized void addIpBan(String ip, String reason, String staffName, Long expiresAt) {
      if (ip != null && !ip.isBlank()) {
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement(
                  "INSERT INTO ip_bans (ip, reason, staff_name, created_at, expires_at, active)\nVALUES (?, ?, ?, ?, ?, 1)\nON CONFLICT(ip) DO UPDATE SET\n    reason = excluded.reason,\n    staff_name = excluded.staff_name,\n    created_at = excluded.created_at,\n    expires_at = excluded.expires_at,\n    active = 1\n"
               )) {
            preparedstatement.setString(1, ip);
            preparedstatement.setString(2, reason);
            preparedstatement.setString(3, staffName);
            preparedstatement.setLong(4, System.currentTimeMillis());
            if (expiresAt == null) {
               preparedstatement.setNull(5, -5);
            } else {
               preparedstatement.setLong(5, expiresAt);
            }

            preparedstatement.executeUpdate();
         } catch (SQLException sqlexception) {
            this.plugin.getLogger().warning("[staff] Failed to add IP ban: " + sqlexception.getMessage());
         }
      }
   }

   public synchronized PunishmentDatabase.PunishmentRecord getActive(UUID uuid, PunishmentDatabase.PunishmentType type) {
      try {
         PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord1;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT * FROM punishments\nWHERE uuid = ? AND type = ? AND active = 1\nORDER BY created_at DESC LIMIT 1\n")) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setString(2, type.name());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord = this.readPunishment(resultset);
               if (punishmentdatabase$punishmentrecord.isExpired()) {
                  this.expirePunishment(punishmentdatabase$punishmentrecord.id());
                  return null;
               }

               punishmentdatabase$punishmentrecord1 = punishmentdatabase$punishmentrecord;
            }
         }

         return punishmentdatabase$punishmentrecord1;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized PunishmentDatabase.PunishmentRecord getActiveIpBan(String ip) {
      if (ip != null && !ip.isBlank()) {
         PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord = this.getActiveIpBanFromTable(ip);
         return punishmentdatabase$punishmentrecord != null ? punishmentdatabase$punishmentrecord : this.getActiveIpBanFromPunishments(ip);
      } else {
         return null;
      }
   }

   private PunishmentDatabase.PunishmentRecord getActiveIpBanFromTable(String ip) {
      try {
         PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT * FROM ip_bans WHERE ip = ? AND active = 1")) {
            preparedstatement.setString(1, ip);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               long i = resultset.getLong("expires_at");
               if (!resultset.wasNull() && i > 0L && System.currentTimeMillis() >= i) {
                  this.deactivateIpBan(ip);
                  return null;
               }

               punishmentdatabase$punishmentrecord = new PunishmentDatabase.PunishmentRecord(
                  -1L,
                  UUID.randomUUID(),
                  ip,
                  resultset.getString("staff_name"),
                  PunishmentDatabase.PunishmentType.IP_BAN,
                  resultset.getString("reason"),
                  resultset.getLong("created_at"),
                  resultset.wasNull() ? null : i,
                  true,
                  ip
               );
            }
         }

         return punishmentdatabase$punishmentrecord;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   private PunishmentDatabase.PunishmentRecord getActiveIpBanFromPunishments(String ip) {
      long i = System.currentTimeMillis();

      try {
         PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord1;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("SELECT * FROM punishments\nWHERE ip = ? AND type = 'IP_BAN' AND active = 1\nORDER BY created_at DESC LIMIT 1\n")) {
            preparedstatement.setString(1, ip);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               if (!resultset.next()) {
                  return null;
               }

               PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord = this.readPunishment(resultset);
               if (punishmentdatabase$punishmentrecord.expiresAt() != null
                  && punishmentdatabase$punishmentrecord.expiresAt() > 0L
                  && i >= punishmentdatabase$punishmentrecord.expiresAt()) {
                  this.expirePunishment(punishmentdatabase$punishmentrecord.id());
                  return null;
               }

               punishmentdatabase$punishmentrecord1 = punishmentdatabase$punishmentrecord;
            }
         }

         return punishmentdatabase$punishmentrecord1;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   public synchronized boolean hasAnyIpBlock(String ip) {
      return this.getActiveIpBan(ip) != null;
   }

   public synchronized int countActivePunishments(UUID uuid, PunishmentDatabase.PunishmentType type) {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT COUNT(*) FROM punishments WHERE uuid = ? AND type = ?")) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setString(2, type.name());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               i = resultset.next() ? resultset.getInt(1) : 0;
            }
         }

         return i;
      } catch (SQLException sqlexception) {
         return 0;
      }
   }

   public synchronized List<PunishmentDatabase.PunishmentRecord> history(UUID uuid, PunishmentDatabase.PunishmentType type, int limit) {
      List<PunishmentDatabase.PunishmentRecord> list = new ArrayList<>();

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("SELECT * FROM punishments WHERE uuid = ? AND type = ?\nORDER BY created_at DESC LIMIT ?\n")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, type.name());
         preparedstatement.setInt(3, Math.max(1, limit));

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               list.add(this.readPunishment(resultset));
            }
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[staff] Failed to load history: " + sqlexception.getMessage());
      }

      return list;
   }

   public synchronized Set<String> knownPlayerNames() {
      Set<String> set = new LinkedHashSet<>();

      try (
         Statement statement = this.connection.createStatement();
         ResultSet resultset = statement.executeQuery("SELECT DISTINCT player_name FROM punishments UNION SELECT DISTINCT player_name FROM player_ips");
      ) {
         while (resultset.next()) {
            String s = resultset.getString(1);
            if (s != null && !s.isBlank()) {
               set.add(s);
            }
         }
      } catch (SQLException sqlexception) {
      }

      return set;
   }

   public synchronized List<String> activePunishedPlayerNames(PunishmentDatabase.PunishmentType type) {
      List<String> list = new ArrayList<>();
      long i = System.currentTimeMillis();

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("SELECT DISTINCT player_name, expires_at FROM punishments\nWHERE type = ? AND active = 1\nORDER BY player_name COLLATE NOCASE\n")) {
         preparedstatement.setString(1, type.name());

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               long j = resultset.getLong("expires_at");
               if (resultset.wasNull() || j <= 0L || i < j) {
                  String s = resultset.getString("player_name");
                  if (s != null && !s.isBlank()) {
                     list.add(s);
                  }
               }
            }
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to list active " + type + " names: " + sqlexception.getMessage());
      }

      return list;
   }

   public synchronized List<String> activeIpBans() {
      Set<String> set = new LinkedHashSet<>();
      long i = System.currentTimeMillis();

      try (
         PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT ip, expires_at FROM ip_bans WHERE active = 1 ORDER BY ip");
         ResultSet resultset = preparedstatement.executeQuery();
      ) {
         while (resultset.next()) {
            long j = resultset.getLong("expires_at");
            if (resultset.wasNull() || j <= 0L || i < j) {
               String s = resultset.getString("ip");
               if (s != null && !s.isBlank()) {
                  set.add(s);
               }
            }
         }
      } catch (SQLException sqlexception1) {
         this.plugin.getLogger().warning("[punishments] Failed to list active IP bans: " + sqlexception1.getMessage());
      }

      try (
         PreparedStatement preparedstatement1 = this.connection
            .prepareStatement(
               "SELECT DISTINCT ip, expires_at FROM punishments\nWHERE type = 'IP_BAN' AND active = 1 AND ip IS NOT NULL AND ip != ''\nORDER BY ip\n"
            );
         ResultSet resultset1 = preparedstatement1.executeQuery();
      ) {
         while (resultset1.next()) {
            long k = resultset1.getLong("expires_at");
            if (resultset1.wasNull() || k <= 0L || i < k) {
               String s1 = resultset1.getString("ip");
               if (s1 != null && !s1.isBlank()) {
                  set.add(s1);
               }
            }
         }
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to list IP_BAN punishments: " + sqlexception.getMessage());
      }

      return new ArrayList<>(set);
   }

   private PunishmentDatabase.PunishmentRecord readPunishment(ResultSet rs) throws SQLException {
      long i = rs.getLong("expires_at");
      return new PunishmentDatabase.PunishmentRecord(
         rs.getLong("id"),
         UUID.fromString(rs.getString("uuid")),
         rs.getString("player_name"),
         rs.getString("staff_name"),
         PunishmentDatabase.PunishmentType.valueOf(rs.getString("type")),
         rs.getString("reason"),
         rs.getLong("created_at"),
         rs.wasNull() ? null : i,
         rs.getInt("active") == 1,
         rs.getString("ip")
      );
   }

   private void expirePunishment(long id) {
      try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE punishments SET active = 0 WHERE id = ?")) {
         preparedstatement.setLong(1, id);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
      }
   }

   public synchronized void close() {
      if (this.connection != null) {
         try {
            this.connection.close();
         } catch (SQLException sqlexception) {
         }

         this.connection = null;
      }
   }

   public synchronized int revokeActiveBansExceptDoxxing() {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection
               .prepareStatement("UPDATE punishments SET active = 0 WHERE type = 'BAN' AND active = 1 AND doxxed = 0")) {
            i = preparedstatement.executeUpdate();
         }

         return i;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to revoke bans: " + sqlexception.getMessage());
         return 0;
      }
   }

   public synchronized int revokeActiveMutes() {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE punishments SET active = 0 WHERE type = 'MUTE' AND active = 1")) {
            i = preparedstatement.executeUpdate();
         }

         return i;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to revoke mutes: " + sqlexception.getMessage());
         return 0;
      }
   }

   public synchronized int revokeActiveWarnings() {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE punishments SET active = 0 WHERE type = 'WARN' AND active = 1")) {
            i = preparedstatement.executeUpdate();
         }

         return i;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to revoke warnings: " + sqlexception.getMessage());
         return 0;
      }
   }

   public synchronized int deleteKicks() {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM punishments WHERE type = 'KICK'")) {
            i = preparedstatement.executeUpdate();
         }

         return i;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to delete kicks: " + sqlexception.getMessage());
         return 0;
      }
   }

   public synchronized int deleteHistory() {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM punishments WHERE active = 0")) {
            i = preparedstatement.executeUpdate();
         }

         return i;
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to delete history: " + sqlexception.getMessage());
         return 0;
      }
   }

   public synchronized int revokeAllIpBans() {
      int i = 0;

      try (PreparedStatement preparedstatement = this.connection.prepareStatement("UPDATE ip_bans SET active = 0 WHERE active = 1")) {
         i += preparedstatement.executeUpdate();
      } catch (SQLException sqlexception1) {
         this.plugin.getLogger().warning("[punishments] Failed to revoke IP bans: " + sqlexception1.getMessage());
      }

      try (PreparedStatement preparedstatement1 = this.connection.prepareStatement("UPDATE punishments SET active = 0 WHERE type = 'IP_BAN' AND active = 1")) {
         i += preparedstatement1.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[punishments] Failed to revoke IP_BAN punishments: " + sqlexception.getMessage());
      }

      return i;
   }

   public static record AltAccount(String name, UUID uuid) {
   }

   public static record PunishmentRecord(
      long id,
      UUID uuid,
      String playerName,
      String staffName,
      PunishmentDatabase.PunishmentType type,
      String reason,
      long createdAt,
      Long expiresAt,
      boolean active,
      String ip
   ) {
      public boolean isExpired() {
         return this.expiresAt != null && this.expiresAt > 0L && System.currentTimeMillis() >= this.expiresAt;
      }

      public boolean isPermanent() {
         return this.expiresAt == null || this.expiresAt <= 0L;
      }
   }

   public static enum PunishmentType {
      BAN,
      MUTE,
      IP_BAN,
      WARN,
      KICK;
   }
}

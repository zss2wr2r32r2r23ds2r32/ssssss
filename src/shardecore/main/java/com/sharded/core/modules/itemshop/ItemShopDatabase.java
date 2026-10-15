package com.sharded.core.modules.itemshop;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ItemShopDatabase {
   private final ShardedCore plugin;
   private Connection connection;

   public ItemShopDatabase(ShardedCore plugin, File folder) throws SQLException {
      this.plugin = plugin;
      File file = new File(folder, "itemshop.db");
      folder.mkdirs();
      this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
      try (Statement statement = this.connection.createStatement()) {
         statement.execute("PRAGMA journal_mode=WAL");
         statement.execute(
            "CREATE TABLE IF NOT EXISTS player_tokens (uuid TEXT PRIMARY KEY, balance INTEGER NOT NULL DEFAULT 0)"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS token_transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, amount INTEGER NOT NULL, reason TEXT, source TEXT, created_at INTEGER NOT NULL)"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS itemshop_rotation (server TEXT NOT NULL, item_type TEXT NOT NULL, item_id TEXT NOT NULL, slot_index INTEGER NOT NULL, expires_at INTEGER NOT NULL, PRIMARY KEY (server, item_type, slot_index))"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS itemshop_rotation_history (id INTEGER PRIMARY KEY AUTOINCREMENT, server TEXT NOT NULL, item_id TEXT NOT NULL, rotated_at INTEGER NOT NULL)"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS player_cosmetics (uuid TEXT NOT NULL, item_type TEXT NOT NULL, item_id TEXT NOT NULL, obtained_at INTEGER NOT NULL, PRIMARY KEY (uuid, item_type, item_id))"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS player_equipped (uuid TEXT PRIMARY KEY, hat_id TEXT, tag_id TEXT)"
         );
         statement.execute(
            "CREATE TABLE IF NOT EXISTS itemshop_purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, item_type TEXT NOT NULL, item_id TEXT NOT NULL, rarity TEXT, price INTEGER NOT NULL, purchased_at INTEGER NOT NULL)"
         );
      }
   }

   public synchronized void upsertPlayerTokens(UUID uuid, long balance) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "INSERT INTO player_tokens (uuid, balance) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance"
      )) {
         ps.setString(1, uuid.toString());
         ps.setLong(2, balance);
         ps.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] player_tokens: " + ex.getMessage());
      }
   }

   public synchronized void logTransaction(UUID uuid, long amount, String reason, String source) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "INSERT INTO token_transactions (uuid, amount, reason, source, created_at) VALUES (?, ?, ?, ?, ?)"
      )) {
         ps.setString(1, uuid.toString());
         ps.setLong(2, amount);
         ps.setString(3, reason);
         ps.setString(4, source);
         ps.setLong(5, System.currentTimeMillis());
         ps.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] token_transactions: " + ex.getMessage());
      }
   }

   public synchronized List<ItemShopTypes.Listing> loadRotation(String server) {
      List<ItemShopTypes.Listing> list = new ArrayList<>();
      try (PreparedStatement ps = this.connection.prepareStatement(
         "SELECT item_type, item_id, slot_index FROM itemshop_rotation WHERE server = ? ORDER BY item_type, slot_index"
      )) {
         ps.setString(1, server);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               list.add(new ItemShopTypes.Listing(rs.getString("item_type"), rs.getString("item_id"), rs.getInt("slot_index")));
            }
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] load rotation: " + ex.getMessage());
      }
      return list;
   }

   public synchronized long loadExpiry(String server) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "SELECT expires_at FROM itemshop_rotation WHERE server = ? LIMIT 1"
      )) {
         ps.setString(1, server);
         try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong("expires_at") : 0L;
         }
      } catch (SQLException ex) {
         return 0L;
      }
   }

   public synchronized void saveRotation(String server, List<ItemShopTypes.Listing> listings, long expiresAt) {
      try (PreparedStatement del = this.connection.prepareStatement("DELETE FROM itemshop_rotation WHERE server = ?")) {
         del.setString(1, server);
         del.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] clear rotation: " + ex.getMessage());
      }
      try (PreparedStatement ps = this.connection.prepareStatement(
         "INSERT INTO itemshop_rotation (server, item_type, item_id, slot_index, expires_at) VALUES (?, ?, ?, ?, ?)"
      )) {
         for (ItemShopTypes.Listing listing : listings) {
            ps.setString(1, server);
            ps.setString(2, listing.type());
            ps.setString(3, listing.id());
            ps.setInt(4, listing.slotIndex());
            ps.setLong(5, expiresAt);
            ps.addBatch();
         }
         ps.executeBatch();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] save rotation: " + ex.getMessage());
      }
      long now = System.currentTimeMillis();
      try (PreparedStatement hist = this.connection.prepareStatement(
         "INSERT INTO itemshop_rotation_history (server, item_id, rotated_at) VALUES (?, ?, ?)"
      )) {
         for (ItemShopTypes.Listing listing : listings) {
            hist.setString(1, server);
            hist.setString(2, listing.id());
            hist.setLong(3, now);
            hist.addBatch();
         }
         hist.executeBatch();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] rotation history: " + ex.getMessage());
      }
   }

   public synchronized Set<String> recentIds(String server, int lastRotations) {
      Set<String> ids = new HashSet<>();
      if (lastRotations <= 0) {
         return ids;
      }
      try (PreparedStatement ps = this.connection.prepareStatement(
         "SELECT DISTINCT rotated_at FROM itemshop_rotation_history WHERE server = ? ORDER BY rotated_at DESC LIMIT ?"
      )) {
         ps.setString(1, server);
         ps.setInt(2, lastRotations);
         List<Long> stamps = new ArrayList<>();
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               stamps.add(rs.getLong(1));
            }
         }
         if (stamps.isEmpty()) {
            return ids;
         }
         StringBuilder in = new StringBuilder();
         for (int i = 0; i < stamps.size(); i++) {
            if (i > 0) {
               in.append(',');
            }
            in.append('?');
         }
         try (PreparedStatement items = this.connection.prepareStatement(
            "SELECT item_id FROM itemshop_rotation_history WHERE server = ? AND rotated_at IN (" + in + ")"
         )) {
            items.setString(1, server);
            for (int i = 0; i < stamps.size(); i++) {
               items.setLong(i + 2, stamps.get(i));
            }
            try (ResultSet rs = items.executeQuery()) {
               while (rs.next()) {
                  ids.add(rs.getString(1));
               }
            }
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] history: " + ex.getMessage());
      }
      return ids;
   }

   public synchronized Map<UUID, Set<String>> loadOwned(String type) {
      Map<UUID, Set<String>> map = new HashMap<>();
      try (PreparedStatement ps = this.connection.prepareStatement(
         "SELECT uuid, item_id FROM player_cosmetics WHERE item_type = ?"
      )) {
         ps.setString(1, type);
         try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
               UUID uuid = UUID.fromString(rs.getString("uuid"));
               map.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(rs.getString("item_id"));
            }
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] owned: " + ex.getMessage());
      }
      return map;
   }

   public synchronized void grant(UUID uuid, String type, String id) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "INSERT OR IGNORE INTO player_cosmetics (uuid, item_type, item_id, obtained_at) VALUES (?, ?, ?, ?)"
      )) {
         ps.setString(1, uuid.toString());
         ps.setString(2, type);
         ps.setString(3, id);
         ps.setLong(4, System.currentTimeMillis());
         ps.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] grant: " + ex.getMessage());
      }
   }

   public synchronized void revoke(UUID uuid, String type, String id) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "DELETE FROM player_cosmetics WHERE uuid = ? AND item_type = ? AND item_id = ?"
      )) {
         ps.setString(1, uuid.toString());
         ps.setString(2, type);
         ps.setString(3, id);
         ps.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] revoke: " + ex.getMessage());
      }
   }

   public synchronized Equipped loadEquipped(UUID uuid) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "SELECT hat_id, tag_id FROM player_equipped WHERE uuid = ?"
      )) {
         ps.setString(1, uuid.toString());
         try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
               return new Equipped(rs.getString("hat_id"), rs.getString("tag_id"));
            }
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] equipped: " + ex.getMessage());
      }
      return new Equipped(null, null);
   }

   public synchronized Map<UUID, Equipped> loadAllEquipped() {
      Map<UUID, Equipped> map = new HashMap<>();
      try (Statement st = this.connection.createStatement(); ResultSet rs = st.executeQuery("SELECT uuid, hat_id, tag_id FROM player_equipped")) {
         while (rs.next()) {
            map.put(UUID.fromString(rs.getString("uuid")), new Equipped(rs.getString("hat_id"), rs.getString("tag_id")));
         }
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] equipped all: " + ex.getMessage());
      }
      return map;
   }

   public synchronized void saveEquipped(UUID uuid, String hatId, String tagId) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "INSERT INTO player_equipped (uuid, hat_id, tag_id) VALUES (?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET hat_id = excluded.hat_id, tag_id = excluded.tag_id"
      )) {
         ps.setString(1, uuid.toString());
         ps.setString(2, hatId);
         ps.setString(3, tagId);
         ps.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] save equipped: " + ex.getMessage());
      }
   }

   public synchronized void logPurchase(UUID uuid, String type, String id, String rarity, long price) {
      try (PreparedStatement ps = this.connection.prepareStatement(
         "INSERT INTO itemshop_purchases (uuid, item_type, item_id, rarity, price, purchased_at) VALUES (?, ?, ?, ?, ?, ?)"
      )) {
         ps.setString(1, uuid.toString());
         ps.setString(2, type);
         ps.setString(3, id);
         ps.setString(4, rarity);
         ps.setLong(5, price);
         ps.setLong(6, System.currentTimeMillis());
         ps.executeUpdate();
      } catch (SQLException ex) {
         this.plugin.getLogger().warning("[itemshop] purchase log: " + ex.getMessage());
      }
   }

   public synchronized void close() {
      try {
         if (this.connection != null && !this.connection.isClosed()) {
            this.connection.close();
         }
      } catch (SQLException ignored) {
      }
      this.connection = null;
   }

   public record Equipped(String hatId, String tagId) {
   }
}

package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerStateStore {
   private final ShardedCore plugin;
   private Connection connection;
   private final Map<String, PlayerStateStore.CacheEntry> cache = new ConcurrentHashMap<>();
   private final Map<String, PlayerStateStore.PendingWrite> pending = new ConcurrentHashMap<>();
   private volatile int flushTaskId = -1;
   private static final long FLUSH_DELAY_TICKS = 40L;

   public PlayerStateStore(ShardedCore plugin) {
      this.plugin = plugin;

      try {
         File file1 = new File(plugin.getDataFolder(), "player-state.db");
         this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

         try (Statement statement = this.connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute(
               "CREATE TABLE IF NOT EXISTS player_state (\n    uuid TEXT NOT NULL,\n    state_key TEXT NOT NULL,\n    bool_value INTEGER,\n    long_value INTEGER,\n    string_value TEXT,\n    PRIMARY KEY (uuid, state_key)\n)\n"
            );
         }

         this.migrateFromYaml();
      } catch (SQLException sqlexception) {
         throw new IllegalStateException("Could not open player-state database", sqlexception);
      }
   }

   private static String cacheKey(UUID uuid, String key) {
      return uuid + "|" + key;
   }

   private void migrateFromYaml() {
      File file1 = new File(this.plugin.getDataFolder(), "players.yml");
      if (file1.exists()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(file1);
         if (yamlconfiguration.getKeys(false).iterator().hasNext()) {
            int i = 0;

            for (String s : yamlconfiguration.getKeys(false)) {
               try {
                  UUID uuid = UUID.fromString(s);
                  ConfigurationSection configurationsection = yamlconfiguration.getConfigurationSection(s);
                  if (configurationsection != null) {
                     for (String s1 : configurationsection.getKeys(false)) {
                        Object object = configurationsection.get(s1);
                        if (object instanceof Boolean obool) {
                           this.setBool(uuid, s1, obool);
                           i++;
                        } else if (object instanceof Number number) {
                           this.setLong(uuid, s1, number.longValue());
                           i++;
                        } else if (object instanceof String s2) {
                           this.setString(uuid, s1, s2);
                           i++;
                        }
                     }
                  }
               } catch (IllegalArgumentException illegalargumentexception) {
               }
            }

            if (i > 0) {
               this.flushPendingSync();
               this.plugin.getLogger().info("Migrated " + i + " player state entries from players.yml to SQLite.");
               File file2 = new File(this.plugin.getDataFolder(), "players.yml.bak");
               if (!file1.renameTo(file2)) {
                  this.plugin.getLogger().warning("Could not rename players.yml after migration.");
               }
            }
         }
      }
   }

   public boolean getBool(UUID uuid, String key, boolean def) {
      PlayerStateStore.CacheEntry playerstatestore$cacheentry = this.cache.get(cacheKey(uuid, key));
      if (playerstatestore$cacheentry != null && playerstatestore$cacheentry.boolVal != null) {
         return playerstatestore$cacheentry.boolVal;
      } else {
         synchronized (this) {
            boolean flag;
            try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT bool_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
               preparedstatement.setString(1, uuid.toString());
               preparedstatement.setString(2, key);

               try (ResultSet resultset = preparedstatement.executeQuery()) {
                  if (!resultset.next() || resultset.getObject("bool_value") == null) {
                     return def;
                  }

                  boolean flag1 = resultset.getInt("bool_value") == 1;
                  this.putCache(uuid, key, new PlayerStateStore.CacheEntry(flag1, null, null));
                  flag = flag1;
               }
            } catch (SQLException sqlexception) {
               this.plugin.getLogger().warning("Failed to read bool state " + key + ": " + sqlexception.getMessage());
               return def;
            }

            return flag;
         }
      }
   }

   public void setBool(UUID uuid, String key, boolean value) {
      this.putCache(uuid, key, new PlayerStateStore.CacheEntry(value, null, null));
      this.queueWrite(new PlayerStateStore.PendingWrite(uuid, key, value ? 1 : 0, null, null));
   }

   public long getLong(UUID uuid, String key, long def) {
      PlayerStateStore.CacheEntry playerstatestore$cacheentry = this.cache.get(cacheKey(uuid, key));
      if (playerstatestore$cacheentry != null && playerstatestore$cacheentry.longVal != null) {
         return playerstatestore$cacheentry.longVal;
      } else {
         synchronized (this) {
            long i;
            try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT long_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
               preparedstatement.setString(1, uuid.toString());
               preparedstatement.setString(2, key);

               try (ResultSet resultset = preparedstatement.executeQuery()) {
                  if (!resultset.next() || resultset.getObject("long_value") == null) {
                     return def;
                  }

                  long j = resultset.getLong("long_value");
                  this.putCache(uuid, key, new PlayerStateStore.CacheEntry(null, j, null));
                  i = j;
               }
            } catch (SQLException sqlexception) {
               this.plugin.getLogger().warning("Failed to read long state " + key + ": " + sqlexception.getMessage());
               return def;
            }

            return i;
         }
      }
   }

   public void setLong(UUID uuid, String key, long value) {
      this.putCache(uuid, key, new PlayerStateStore.CacheEntry(null, value, null));
      this.queueWrite(new PlayerStateStore.PendingWrite(uuid, key, null, value, null));
   }

   public String getString(UUID uuid, String key, String def) {
      PlayerStateStore.CacheEntry playerstatestore$cacheentry = this.cache.get(cacheKey(uuid, key));
      if (playerstatestore$cacheentry != null && playerstatestore$cacheentry.stringVal != null) {
         return playerstatestore$cacheentry.stringVal;
      } else {
         synchronized (this) {
            String s;
            try (PreparedStatement preparedstatement = this.connection
                  .prepareStatement("SELECT string_value FROM player_state WHERE uuid = ? AND state_key = ?")) {
               preparedstatement.setString(1, uuid.toString());
               preparedstatement.setString(2, key);

               try (ResultSet resultset = preparedstatement.executeQuery()) {
                  if (!resultset.next() || resultset.getString("string_value") == null) {
                     return def;
                  }

                  String s1 = resultset.getString("string_value");
                  this.putCache(uuid, key, new PlayerStateStore.CacheEntry(null, null, s1));
                  s = s1;
               }
            } catch (SQLException sqlexception) {
               this.plugin.getLogger().warning("Failed to read string state " + key + ": " + sqlexception.getMessage());
               return def;
            }

            return s;
         }
      }
   }

   public void setString(UUID uuid, String key, String value) {
      this.putCache(uuid, key, new PlayerStateStore.CacheEntry(null, null, value));
      this.queueWrite(new PlayerStateStore.PendingWrite(uuid, key, null, null, value));
   }

   private void putCache(UUID uuid, String key, PlayerStateStore.CacheEntry entry) {
      this.cache.put(cacheKey(uuid, key), entry);
   }

   private void queueWrite(PlayerStateStore.PendingWrite write) {
      this.pending.put(write.cacheKey(), write);
      this.scheduleFlush();
   }

   private void scheduleFlush() {
      if (this.flushTaskId < 0) {
         this.flushTaskId = Bukkit.getScheduler().runTaskLaterAsynchronously(this.plugin, () -> {
            this.flushTaskId = -1;
            this.flushPendingAsync();
         }, 40L).getTaskId();
      }
   }

   private void flushPendingAsync() {
      if (!this.pending.isEmpty()) {
         Map<String, PlayerStateStore.PendingWrite> map = Map.copyOf(this.pending);
         this.pending.keySet().removeAll(map.keySet());
         synchronized (this) {
            this.upsertBatch(map.values());
         }
      }
   }

   private void flushPendingSync() {
      if (!this.pending.isEmpty()) {
         Map<String, PlayerStateStore.PendingWrite> map = Map.copyOf(this.pending);
         this.pending.clear();
         if (this.flushTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(this.flushTaskId);
            this.flushTaskId = -1;
         }

         synchronized (this) {
            this.upsertBatch(map.values());
         }
      }
   }

   private void upsertBatch(Iterable<PlayerStateStore.PendingWrite> writes) {
      if (this.connection != null) {
         try {
            this.connection.setAutoCommit(false);

            try (PreparedStatement preparedstatement = this.connection
                  .prepareStatement(
                     "INSERT INTO player_state (uuid, state_key, bool_value, long_value, string_value)\nVALUES (?, ?, ?, ?, ?)\nON CONFLICT(uuid, state_key) DO UPDATE SET\n    bool_value = COALESCE(excluded.bool_value, player_state.bool_value),\n    long_value = COALESCE(excluded.long_value, player_state.long_value),\n    string_value = COALESCE(excluded.string_value, player_state.string_value)\n"
                  )) {
               for (PlayerStateStore.PendingWrite playerstatestore$pendingwrite : writes) {
                  preparedstatement.setString(1, playerstatestore$pendingwrite.uuid().toString());
                  preparedstatement.setString(2, playerstatestore$pendingwrite.key());
                  if (playerstatestore$pendingwrite.boolVal() != null) {
                     preparedstatement.setInt(3, playerstatestore$pendingwrite.boolVal());
                  } else {
                     preparedstatement.setNull(3, 4);
                  }

                  if (playerstatestore$pendingwrite.longVal() != null) {
                     preparedstatement.setLong(4, playerstatestore$pendingwrite.longVal());
                  } else {
                     preparedstatement.setNull(4, 4);
                  }

                  if (playerstatestore$pendingwrite.stringVal() != null) {
                     preparedstatement.setString(5, playerstatestore$pendingwrite.stringVal());
                  } else {
                     preparedstatement.setNull(5, 12);
                  }

                  preparedstatement.addBatch();
               }

               preparedstatement.executeBatch();
            }

            this.connection.commit();
         } catch (SQLException sqlexception2) {
            try {
               this.connection.rollback();
            } catch (SQLException sqlexception1) {
            }

            this.plugin.getLogger().warning("Failed to flush player state: " + sqlexception2.getMessage());
         } finally {
            try {
               this.connection.setAutoCommit(true);
            } catch (SQLException sqlexception) {
            }
         }
      }
   }

   public synchronized void clear(UUID uuid) {
      this.cache.keySet().removeIf(k -> k.startsWith(uuid + "|"));
      Iterator<Entry<String, PlayerStateStore.PendingWrite>> iterator = this.pending.entrySet().iterator();

      while (iterator.hasNext()) {
         if (iterator.next().getValue().uuid().equals(uuid)) {
            iterator.remove();
         }
      }

      try (PreparedStatement preparedstatement = this.connection.prepareStatement("DELETE FROM player_state WHERE uuid = ?")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("Failed to clear player state: " + sqlexception.getMessage());
      }
   }

   public void saveNow() {
      this.flushPendingSync();
   }

   public synchronized void close() {
      this.flushPendingSync();

      try {
         if (this.connection != null && !this.connection.isClosed()) {
            this.connection.close();
         }
      } catch (SQLException sqlexception) {
      }

      this.connection = null;
      this.cache.clear();
   }

   private static record CacheEntry(Boolean boolVal, Long longVal, String stringVal) {
   }

   private static record PendingWrite(UUID uuid, String key, Integer boolVal, Long longVal, String stringVal) {
      String cacheKey() {
         return this.uuid + "|" + this.key;
      }
   }
}

package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import java.io.File;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.file.YamlConfiguration;

public final class GameEventCoordinator {
   private static GameEventCoordinator instance;
   private final ShardedCore plugin;
   private final File file;
   private YamlConfiguration data;
   private long nextOutpostMs;
   private long nextKothMs;
   private boolean outpostActive;
   private boolean kothActive;
   private final EventBossBar bossBar;

   public GameEventCoordinator(ShardedCore plugin) {
      this.plugin = plugin;
      this.file = new File(plugin.getDataFolder(), "game-events.yml");
      this.bossBar = new EventBossBar();
      this.reload();
      instance = this;
   }

   public static GameEventCoordinator get() {
      return instance;
   }

   public void reload() {
      if (!this.file.exists()) {
         this.data = new YamlConfiguration();
         this.scheduleInitial();
         this.save();
      } else {
         this.data = YamlConfiguration.loadConfiguration(this.file);
         this.nextOutpostMs = this.data.getLong("next-outpost-ms");
         this.nextKothMs = this.data.getLong("next-koth-ms");
         if (this.nextOutpostMs <= 0L && this.nextKothMs <= 0L) {
            this.scheduleInitial();
         }
      }
   }

   private void scheduleInitial() {
      long i = System.currentTimeMillis();
      this.nextOutpostMs = i + this.randomDelayMs();
      this.nextKothMs = i + this.randomDelayMs() + 3600000L;
   }

   private long randomDelayMs() {
      long i = 3600000L;
      long j = 21600000L;
      return ThreadLocalRandom.current().nextLong(i, j);
   }

   public long cooldownMs() {
      return 86400000L;
   }

   public boolean isOutpostActive() {
      return this.outpostActive;
   }

   public boolean isKothActive() {
      return this.kothActive;
   }

   public void setOutpostActive(boolean active) {
      this.outpostActive = active;
      if (!active) {
         this.nextOutpostMs = System.currentTimeMillis() + this.cooldownMs();
         if (this.nextKothMs <= this.nextOutpostMs + 600000L) {
            this.nextKothMs = this.nextOutpostMs + 3600000L;
         }

         this.save();
      }
   }

   public void setKothActive(boolean active) {
      this.kothActive = active;
      if (!active) {
         this.nextKothMs = System.currentTimeMillis() + this.cooldownMs();
         if (this.nextOutpostMs <= this.nextKothMs + 600000L) {
            this.nextOutpostMs = this.nextKothMs + 3600000L;
         }

         this.save();
      }
   }

   public boolean canStartOutpost() {
      return !this.outpostActive && !this.kothActive && System.currentTimeMillis() >= this.nextOutpostMs;
   }

   public boolean canStartKoth() {
      return !this.kothActive && !this.outpostActive && System.currentTimeMillis() >= this.nextKothMs;
   }

   public long millisUntilOutpost() {
      return this.outpostActive ? 0L : Math.max(0L, this.nextOutpostMs - System.currentTimeMillis());
   }

   public long millisUntilKoth() {
      return this.kothActive ? 0L : Math.max(0L, this.nextKothMs - System.currentTimeMillis());
   }

   public EventBossBar bossBar() {
      return this.bossBar;
   }

   public void shutdown() {
      this.bossBar.shutdown();
   }

   public void save() {
      this.data.set("next-outpost-ms", this.nextOutpostMs);
      this.data.set("next-koth-ms", this.nextKothMs);

      try {
         this.data.save(this.file);
      } catch (Exception exception) {
         this.plugin.getLogger().warning("Could not save game-events.yml: " + exception.getMessage());
      }
   }
}

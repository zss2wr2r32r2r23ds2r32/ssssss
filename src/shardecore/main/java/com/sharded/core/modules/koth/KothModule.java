package com.sharded.core.modules.koth;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.EventLocatorBar;
import com.sharded.core.util.EventRewards;
import com.sharded.core.util.EventSounds;
import com.sharded.core.util.EventTabScoreboard;
import com.sharded.core.util.GameEventCoordinator;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TimeFormat;
import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.MusicInstrument;
import org.bukkit.boss.BarColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class KothModule extends Module implements CommandExecutor, TabCompleter {
   private final RegionSetup setup = new RegionSetup();
   private CuboidRegion region;
   private GameEventCoordinator coordinator;
   private boolean active;
   private long eventEndsAt;
   private long eventDurationMs;
   private final Map<UUID, Double> points = new HashMap<>();
   private final Set<UUID> inside = new HashSet<>();
   private int tickTask = -1;
   private long eventStartedAt;
   private long lastSoundAt;

   public KothModule(ShardedCore plugin) {
      super(plugin, "koth");
   }

   @Override
   protected void onEnable() {
      if (GameEventCoordinator.get() == null) {
         this.coordinator = new GameEventCoordinator(this.plugin);
      } else {
         this.coordinator = GameEventCoordinator.get();
      }

      this.reloadRegion();
      this.ensureHudDefaults();
      this.registerCommand("koth", this);
      this.tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::tick, 20L, 20L);
   }

   @Override
   protected void onDisable() {
      if (this.tickTask >= 0) {
         Bukkit.getScheduler().cancelTask(this.tickTask);
      }

      this.active = false;
      this.points.clear();
      if (this.coordinator != null) {
         this.coordinator.bossBar().hide("koth");
      }
      EventLocatorBar eventlocatorbar = EventLocatorBar.get();
      if (eventlocatorbar != null) {
         eventlocatorbar.hide("koth");
      }
   }

   private void reloadRegion() {
      this.region = CuboidRegion.fromSection(this.config.getConfigurationSection("region"));
   }

   private void ensureHudDefaults() {
      if (this.config.getInt("config-version", 0) >= 7
            && this.config.getBoolean("bossbar", true)
            && this.config.getBoolean("locator-bar", true)) {
         return;
      }
      this.config.set("bossbar", true);
      this.config.set("locator-bar", true);
      this.config.set("config-version", 7);
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[koth] Could not save config: " + exception.getMessage());
      }
   }

   public long millisUntilStart() {
      if (this.active) {
         return Math.max(0L, this.eventEndsAt - System.currentTimeMillis());
      } else {
         return this.millisUntilNext();
      }
   }

   public long millisUntilNext() {
      return this.coordinator == null ? 0L : this.coordinator.millisUntilKoth();
   }

   public boolean isActive() {
      return this.active;
   }

   public Location regionCenter() {
      return this.region == null ? null : this.region.center();
   }

   public boolean isInside(Player player) {
      return this.region != null && this.region.contains(player);
   }

   public String leaderName() {
      return this.points.entrySet().stream().max(Entry.comparingByValue()).map(e -> OfflinePlayers.name(e.getKey())).orElse("None");
   }

   public double leaderPoints() {
      return this.points.values().stream().max(Double::compare).orElse(0.0);
   }

   public String modulePrefix() {
      return this.config.getString("prefix", "&#FF005D&lKOTH &8▷ &r");
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         if (sender instanceof Player player1) {
            this.send(player1, "info", new String[]{"%time%", TimeFormat.hms(this.millisUntilStart())});
            return true;
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         if (s.equals("start")) {
            if (!sender.hasPermission("sharded.koth.admin")) {
               this.send(sender, "no-permission", new String[0]);
               return true;
            } else if (this.active) {
               this.send(sender, "already-active", new String[0]);
               return true;
            } else if (this.region == null) {
               this.send(sender, "no-region", new String[0]);
               return true;
            } else {
               this.startEvent();
               this.send(sender, "started", new String[0]);
               return true;
            }
         } else if (sender instanceof Player player) {
            if (!player.hasPermission("sharded.koth.admin")) {
               this.send(sender, "no-permission", new String[0]);
               return true;
            } else if (s.equals("pos1")) {
               this.setup.setPos1(player, player.getLocation());
               this.send(player, "pos1-set", new String[0]);
               return true;
            } else if (s.equals("pos2")) {
               this.setup.setPos2(player, player.getLocation());
               this.send(player, "pos2-set", new String[0]);
               return true;
            } else if (s.equals("setregion")) {
               if (!this.isSpawnWorld(player.getWorld().getName())) {
                  this.send(player, "spawn-world-only", new String[0]);
                  return true;
               } else {
                  CuboidRegion cuboidregion = this.setup.build(player);
                  if (cuboidregion == null) {
                     this.send(player, "need-positions", new String[0]);
                     return true;
                  } else {
                     this.region = cuboidregion;
                     cuboidregion.write(this.config.createSection("region"));
                     this.saveConfig();
                     this.send(player, "region-set", new String[0]);
                     return true;
                  }
               }
            } else {
               this.send(player, "usage", new String[0]);
               return true;
            }
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onPlace(BlockPlaceEvent event) {
      if (this.region != null) {
         if (this.region.contains(event.getBlock().getLocation())) {
            event.setCancelled(true);
            this.send(event.getPlayer(), "no-place", new String[0]);
         }
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (this.active && this.coordinator != null) {
         this.coordinator.bossBar().syncPlayers();
      }
   }

   private boolean isSpawnWorld(String world) {
      List<String> list = this.config.getStringList("allowed-worlds");
      if (list.isEmpty()) {
         list = List.of("spawn");
      }

      for (String s : list) {
         if (s.equalsIgnoreCase(world)) {
            return true;
         }
      }

      return false;
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHit(EntityDamageByEntityEvent event) {
      if (this.active && this.region != null) {
         if (event.getEntity() instanceof Player player) {
            Player player3 = null;
            if (event.getDamager() instanceof Player player1) {
               player3 = player1;
            } else if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player2) {
               player3 = player2;
            }

            if (player3 != null) {
               if (this.region.contains(player.getLocation()) || this.region.contains(player3.getLocation())) {
                  this.addPoints(player3.getUniqueId(), this.config.getDouble("points-per-hit", 5.0));
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (!this.active || this.region == null || !event.hasChangedBlock()) {
         return;
      }
      if (this.active && this.region != null) {
         if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            if (this.region.contains(event.getTo())) {
               this.inside.add(event.getPlayer().getUniqueId());
            } else {
               this.inside.remove(event.getPlayer().getUniqueId());
            }
         }
      }
   }

   private void tick() {
      if (this.region != null) {
         if (!this.active) {
            if (this.coordinator != null && this.coordinator.canStartKoth()) {
               this.startEvent();
            }
         } else {
            long i = System.currentTimeMillis();
            if (i >= this.eventEndsAt) {
               this.finishEvent();
            } else {
               long j = this.config.getLong("max-unclaimed-seconds", 600L) * 1000L;
               if (i - this.eventStartedAt >= j && this.points.isEmpty()) {
                  Bukkit.broadcast(Text.c(this.modulePrefix() + this.raw("timeout", new String[0])));
                  this.finishEvent();
               } else {
                  double d0 = this.config.getDouble("points-per-second-standing", 1.0);

                  for (UUID uuid : this.inside) {
                     this.addPoints(uuid, d0);
                     Player player = Bukkit.getPlayer(uuid);
                     if (player != null) {
                        String s = this.config
                           .getString("actionbar", "&dKOTH &7| &f%points% pts &7| &f%time%")
                           .replace("%points%", String.format(Locale.US, "%.0f", this.points.getOrDefault(uuid, 0.0)))
                           .replace("%time%", TimeFormat.hms(this.eventEndsAt - i));
                        player.sendActionBar(Text.c(this.modulePrefix() + s));
                     }
                  }

                  this.updateBossBar(i);
               }
            }
         }
      }
   }

   private void maybePlayActiveSound() {
      long i = this.config.getLong("active-sound-interval-seconds", 90L) * 1000L;
      long j = System.currentTimeMillis();
      if (i <= 0L || j - this.lastSoundAt >= i) {
         this.lastSoundAt = j;
         MusicInstrument musicinstrument = EventSounds.parseInstrument(
            this.config.getString("active-sound-instrument", "SING_GOAT_HORN"), MusicInstrument.SING_GOAT_HORN
         );
         EventSounds.playInstrumentToWorlds(musicinstrument, this.config.getStringList("allowed-worlds"));
      }
   }

   private void updateBossBar(long now) {
      if (this.coordinator != null) {
         long i = this.eventEndsAt - now;
         String s = this.leaderName();
         double d0 = this.leaderPoints();
         String s1 = this.config
            .getString("bossbar-active", "%prefix%&f%leader% &7(%points% pts) &8| &f%time%")
            .replace("%prefix%", this.modulePrefix())
            .replace("%leader%", s)
            .replace("%points%", String.format(Locale.US, "%.0f", d0))
            .replace("%time%", TimeFormat.hms(i));
         double d1 = this.eventDurationMs <= 0L ? 1.0 : (double)i / (double)this.eventDurationMs;
         this.coordinator.bossBar().show("koth", s1, BarColor.PINK, d1);
         this.coordinator.bossBar().syncPlayers();
      }
   }

   private void addPoints(UUID uuid, double amount) {
      this.points.merge(uuid, amount, Double::sum);
   }

   public double eventPercent() {
      if (!this.active || this.eventDurationMs <= 0L) {
         return 0.0;
      }
      long remaining = Math.max(0L, this.eventEndsAt - System.currentTimeMillis());
      return Math.max(0.0, Math.min(100.0, remaining * 100.0 / (double) this.eventDurationMs));
   }

   public String progressBar() {
      return EventTabScoreboard.bar(this.eventPercent());
   }

   private void startEvent() {
      this.active = true;
      this.points.clear();
      this.inside.clear();
      this.eventStartedAt = System.currentTimeMillis();
      this.lastSoundAt = 0L;
      this.eventDurationMs = this.config.getLong("duration-seconds", 300L) * 1000L;
      this.eventEndsAt = this.eventStartedAt + this.eventDurationMs;
      this.coordinator.setKothActive(true);
      EventTabScoreboard.apply(this.config.getBoolean("tab.enabled", true), this.config.getString("tab.start-command", "tab scoreboard show koth"));
      this.updateBossBar(this.eventStartedAt);
      EventLocatorBar.refresh();
      Bukkit.broadcast(Text.c(this.modulePrefix() + this.raw("broadcast-start", new String[0])));
      this.maybePlayActiveSound();
   }

   private void finishEvent() {
      this.active = false;
      if (this.coordinator != null) {
         this.coordinator.bossBar().hide("koth");
      }
      EventLocatorBar eventlocatorbar = EventLocatorBar.get();
      if (eventlocatorbar != null) {
         eventlocatorbar.hide("koth");
      }
      EventTabScoreboard.apply(this.config.getBoolean("tab.enabled", true), this.config.getString("tab.stop-command", "tab scoreboard hide koth"));

      List<Entry<UUID, Double>> list = this.points.entrySet().stream().sorted(Entry.comparingByValue(Comparator.reverseOrder())).limit(3L).toList();

      for (int i = 0; i < list.size(); i++) {
         int j = i + 1;
         UUID uuid = list.get(i).getKey();
         ConfigurationSection configurationsection = this.config.getConfigurationSection("rewards.rank-" + j);
         long k = configurationsection != null
            ? configurationsection.getLong("tokens", 0L)
            : this.config.getLong("rewards.rank-" + j, 1000L - (long)(j - 1) * 200L);
         if (configurationsection != null) {
            EventRewards.grant(this.plugin, uuid, configurationsection);
         } else {
            TokenService tokenservice = this.plugin.modules().tokens();
            if (tokenservice != null && k > 0L) {
               tokenservice.give(uuid, k);
            }
         }

         Bukkit.broadcast(
            Text.c(
               this.modulePrefix()
                  + this.raw("reward-line", new String[]{"%rank%", String.valueOf(j), "%player%", OfflinePlayers.name(uuid), "%amount%", String.valueOf(k)})
            )
         );
      }

      this.points.clear();
      this.inside.clear();
      this.coordinator.setKothActive(false);
   }

   private void saveConfig() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[koth] Could not save config: " + exception.getMessage());
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.koth.admin")) {
         return List.of();
      } else {
         return args.length == 1 ? TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion", "start") : List.of();
      }
   }
}

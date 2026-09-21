package com.sharded.core.modules.outpost;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class OutpostModule extends Module implements CommandExecutor, TabCompleter {
   private final RegionSetup setup = new RegionSetup();
   private CuboidRegion region;
   private GameEventCoordinator coordinator;
   private boolean active;
   private double capturePercent;
   private UUID capturingPlayer;
   private final Set<UUID> inside = new HashSet<>();
   private int tickTask = -1;
   private long eventStartedAt;
   private long lastSoundAt;
   private boolean contested;

   public OutpostModule(ShardedCore plugin) {
      super(plugin, "outpost");
   }

   @Override
   protected void onEnable() {
      this.coordinator = GameEventCoordinator.get() != null ? GameEventCoordinator.get() : new GameEventCoordinator(this.plugin);
      this.reloadRegion();
      this.ensureHudDefaults();
      this.registerCommand("outpost", this);
      this.tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::tick, 20L, 20L);
   }

   @Override
   protected void onDisable() {
      if (this.tickTask >= 0) {
         Bukkit.getScheduler().cancelTask(this.tickTask);
      }

      this.active = false;
      if (this.coordinator != null) {
         this.coordinator.bossBar().hide("outpost");
      }
      EventLocatorBar eventlocatorbar = EventLocatorBar.get();
      if (eventlocatorbar != null) {
         eventlocatorbar.hide("outpost");
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
      this.saveConfig();
   }

   public long millisUntilStart() {
      return this.coordinator == null ? 0L : this.coordinator.millisUntilOutpost();
   }

   public boolean isActive() {
      return this.active;
   }

   public boolean isInside(Player player) {
      return this.region != null && this.region.contains(player);
   }

   public double capturePercent() {
      return this.capturePercent;
   }

   public boolean isContested() {
      return this.contested;
   }

   public String progressBar() {
      return EventTabScoreboard.bar(this.capturePercent);
   }

   public String contestingName() {
      if (this.inside.isEmpty()) {
         return "N/A";
      }
      if (this.contested) {
         return "Contested";
      }
      return this.capturerName();
   }

   public String capturerName() {
      if (this.contested) {
         return "Contested";
      }
      return this.capturingPlayer == null ? "N/A" : OfflinePlayers.name(this.capturingPlayer);
   }

   public Location regionCenter() {
      return this.region == null ? null : this.region.center();
   }

   public long emptyTimeRemainingMs() {
      long i = this.config.getLong("max-unclaimed-seconds", 600L) * 1000L;
      return Math.max(0L, i - (System.currentTimeMillis() - this.eventStartedAt));
   }

   public String modulePrefix() {
      return this.config.getString("prefix", "&#FF2727&lOUTPOST &8▷ &r");
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
            if (!sender.hasPermission("sharded.outpost.admin")) {
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
            if (!player.hasPermission("sharded.outpost.admin")) {
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

   private void saveConfig() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[outpost] Could not save config: " + exception.getMessage());
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.region != null && this.active && event.getTo() != null) {
         if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            if (event.getTo().getWorld() != null) {
               if (!this.region.world().equalsIgnoreCase(event.getTo().getWorld().getName())) {
                  this.inside.remove(event.getPlayer().getUniqueId());
               } else {
                  Player player = event.getPlayer();
                  if (this.region.contains(event.getTo())) {
                     this.inside.add(player.getUniqueId());
                  } else {
                     this.inside.remove(player.getUniqueId());
                  }
               }
            }
         }
      }
   }

   private void tick() {
      if (this.region != null) {
         if (!this.active) {
            if (this.coordinator != null && this.coordinator.canStartOutpost()) {
               this.startEvent();
            }
         } else {
            this.refreshInside();
            ArrayList<UUID> arraylist = new ArrayList<>(this.inside);
            this.contested = arraylist.size() > 1;
            if (arraylist.size() == 1) {
               UUID uuid = arraylist.getFirst();
               this.capturingPlayer = uuid;
               double d0 = this.config.getDouble("capture-percent-per-second", 1.0);
               this.capturePercent = Math.min(100.0, this.capturePercent + d0);
               Player player1 = Bukkit.getPlayer(uuid);
               if (player1 != null) {
                  String s = this.config
                     .getString("actionbar", "&7Outpost: &f%percent%%")
                     .replace("%percent%", String.format(Locale.US, "%.0f", this.capturePercent));
                  player1.sendActionBar(Text.c(this.modulePrefix() + s));
               }

               if (this.capturePercent >= 100.0) {
                  this.completeCapture(uuid);
                  return;
               }
            } else {
               this.capturingPlayer = null;
               if (this.contested) {
                  for (UUID uuid1 : arraylist) {
                     Player player = Bukkit.getPlayer(uuid1);
                     if (player != null) {
                        player.sendActionBar(Text.c(this.modulePrefix() + this.config.getString("actionbar-contested", "&cContested — solo capture required!")));
                     }
                  }
               }
            }

            this.updateBossBar();
            long i = this.config.getLong("max-unclaimed-seconds", 600L) * 1000L;
            if (System.currentTimeMillis() - this.eventStartedAt >= i && this.capturePercent < 100.0) {
               Bukkit.broadcast(Text.c(this.modulePrefix() + this.raw("timeout", new String[0])));
               this.endEvent();
            }
         }
      }
   }

   private void updateBossBar() {
      if (this.coordinator != null) {
         long i = this.config.getLong("max-unclaimed-seconds", 600L) * 1000L;
         long j = this.emptyTimeRemainingMs();
         String s = this.capturerName();
         String s1;
         double d0;
         if (this.inside.isEmpty()) {
            s1 = this.config
               .getString("bossbar-empty", "%prefix%&fN/A &8| &fEnds in &f%empty_time%")
               .replace("%prefix%", this.modulePrefix())
               .replace("%empty_time%", TimeFormat.hms(j));
            d0 = i <= 0L ? 0.0 : (double)j / (double)i;
         } else {
            s1 = this.config
               .getString("bossbar-active", "%prefix%&f%capturer% &8| &f%percent%%")
               .replace("%prefix%", this.modulePrefix())
               .replace("%capturer%", s)
               .replace("%percent%", String.format(Locale.US, "%.0f", this.capturePercent));
            d0 = this.capturePercent / 100.0;
         }

         this.coordinator.bossBar().show("outpost", s1, BarColor.RED, d0);
         this.coordinator.bossBar().syncPlayers();
      }
   }

   private void maybePlayActiveSound() {
      long i = this.config.getLong("active-sound-interval-seconds", 90L) * 1000L;
      long j = System.currentTimeMillis();
      if (i <= 0L || j - this.lastSoundAt >= i) {
         this.lastSoundAt = j;
         MusicInstrument musicinstrument = EventSounds.parseInstrument(
            this.config.getString("active-sound-instrument", "PONDER_GOAT_HORN"), MusicInstrument.PONDER_GOAT_HORN
         );
         EventSounds.playInstrumentToWorlds(musicinstrument, this.config.getStringList("allowed-worlds"));
      }
   }

   private void refreshInside() {
      this.inside.clear();

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.region.contains(player)) {
            this.inside.add(player.getUniqueId());
         }
      }
   }

   private void startEvent() {
      this.active = true;
      this.capturePercent = 0.0;
      this.capturingPlayer = null;
      this.contested = false;
      this.eventStartedAt = System.currentTimeMillis();
      this.lastSoundAt = 0L;
      this.coordinator.setOutpostActive(true);
      EventTabScoreboard.apply(this.config.getBoolean("tab.enabled", true), this.config.getString("tab.start-command", "tab scoreboard show outpost"));
      this.updateBossBar();
      EventLocatorBar.refresh();
      Bukkit.broadcast(Text.c(this.modulePrefix() + this.raw("broadcast-start", new String[0])));
      this.maybePlayActiveSound();
   }

   private void completeCapture(UUID uuid) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("capture-rewards");
      long i = configurationsection != null
         ? configurationsection.getLong("tokens", this.config.getLong("token-reward", 500L))
         : this.config.getLong("token-reward", 500L);
      if (configurationsection != null) {
         EventRewards.grant(this.plugin, uuid, configurationsection);
      } else {
         TokenService tokenservice = this.plugin.modules().tokens();
         if (tokenservice != null) {
            tokenservice.give(uuid, i);
         }
      }

      String s = OfflinePlayers.name(uuid);
      Player player = Bukkit.getPlayer(uuid);
      if (player != null) {
         s = player.getName();
         this.send(player, "captured", new String[]{"%amount%", String.valueOf(i), "%player%", s});
      }

      this.broadcastCapture(s, i);
      this.endEvent();
   }

   private void broadcastCapture(String playerName, long reward) {
      List<String> list = this.rawList("captured-broadcast", new String[0]);
      if (list != null && !list.isEmpty()) {
         for (String s : list) {
            Bukkit.broadcast(Text.c(s.replace("%player%", playerName).replace("%amount%", String.valueOf(reward))));
         }
      } else {
         Bukkit.broadcast(Text.c("&#00D6FF&l⚔ OUTPOST CAPTURED ⚔"));
         Bukkit.broadcast(Text.c("&7[INFORMATION]"));
         Bukkit.broadcast(Text.c("&#00D6FF| &f" + playerName + " &fhas captured the outpost"));
         Bukkit.broadcast(Text.c("&#00D6FF| &fand has won &#00D6FF" + reward + " tokens &fand a &#00D6FFoutpost key&f!"));
         Bukkit.broadcast(Text.c("&#00D6FF▷ &fReward: &#00D6FF" + reward + " tokens &8+ &#00D6FFoutpost key"));
      }
   }

   private void endEvent() {
      this.active = false;
      this.capturePercent = 0.0;
      this.capturingPlayer = null;
      this.contested = false;
      this.inside.clear();
      if (this.coordinator != null) {
         this.coordinator.bossBar().hide("outpost");
      }

      this.coordinator.setOutpostActive(false);
      EventTabScoreboard.apply(this.config.getBoolean("tab.enabled", true), this.config.getString("tab.stop-command", "tab scoreboard hide outpost"));
      EventLocatorBar eventlocatorbar = EventLocatorBar.get();
      if (eventlocatorbar != null) {
         eventlocatorbar.hide("outpost");
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.outpost.admin")) {
         return List.of();
      } else {
         return args.length == 1 ? TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion", "start") : List.of();
      }
   }
}

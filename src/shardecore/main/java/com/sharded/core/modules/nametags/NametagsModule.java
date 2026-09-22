package com.sharded.core.modules.nametags;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.duel.DuelModule;
import com.sharded.core.modules.teams.TeamDatabase;
import com.sharded.core.modules.teams.TeamsModule;
import com.sharded.core.util.PlaceholderUtil;
import com.sharded.core.util.Text;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerHideEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerShowEntityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class NametagsModule extends Module implements CommandExecutor {
   private final Map<UUID, TextDisplay> displays = new ConcurrentHashMap<>();
   private final Map<UUID, String> lastText = new ConcurrentHashMap<>();
   private final AtomicLong tick = new AtomicLong();
   private NamespacedKey tagKey;
   private BukkitTask followTask;
   private BukkitTask refreshTask;

   public NametagsModule(ShardedCore plugin) {
      super(plugin, "nametags");
   }

   @Override
   protected void onEnable() {
      this.tagKey = new NamespacedKey(this.plugin, "stats_nametag");
      this.purgeStrays();
      if (!this.config.getBoolean("enabled", true)) {
         this.plugin.getLogger().info("[nametags] Disabled in config.");
      } else {
         this.registerCommand("nametags", this);
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
               this.spawn(player);
            }
         }, 20L);
         this.clampRefresh();
         long i = Math.max(1L, this.config.getLong("refresh", 20L));
         if (!this.ride()) {
            this.followTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::followAll, i, i);
         }
         this.refreshTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            this.tick.incrementAndGet();
            this.refreshAll();
         }, i, i);
      }
   }

   @Override
   protected void onDisable() {
      if (this.followTask != null) {
         this.followTask.cancel();
      }

      if (this.refreshTask != null) {
         this.refreshTask.cancel();
      }

      this.followTask = null;
      this.refreshTask = null;

      for (TextDisplay textdisplay : new ArrayList<>(this.displays.values())) {
         if (textdisplay != null) {
            textdisplay.remove();
         }
      }

      this.displays.clear();
      this.lastText.clear();
      this.purgeStrays();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("sharded.nametags.admin")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
         this.loadConfigs();

         for (Player player : Bukkit.getOnlinePlayers()) {
            this.despawn(player.getUniqueId());
            this.spawn(player);
         }

         this.send(sender, "reloaded", new String[]{"%count%", String.valueOf(this.displays.size())});
         return true;
      } else {
         this.send(
            sender,
            "info",
            new String[]{
               "%lines%",
               "2",
               "%offset%",
               String.valueOf(this.yOffset()),
               "%gap%",
               String.valueOf(this.config.getDouble("display.line-gap", 0.25)),
               "%refresh%",
               String.valueOf(Math.max(20L, this.config.getLong("refresh", 20L)))
            }
         );
         this.send(sender, "usage", new String[0]);
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (event.getPlayer().isOnline()) {
            this.spawn(event.getPlayer());
         }
      }, 15L);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent event) {
      this.despawn(event.getPlayer().getUniqueId());
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onTeleport(PlayerTeleportEvent event) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.follow(event.getPlayer()));
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onWorld(PlayerChangedWorldEvent event) {
      this.despawn(event.getPlayer().getUniqueId());
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (event.getPlayer().isOnline()) {
            this.spawn(event.getPlayer());
         }
      }, 5L);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onRespawn(PlayerRespawnEvent event) {
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
         if (event.getPlayer().isOnline()) {
            this.spawn(event.getPlayer());
         }
      }, 5L);
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onGameMode(PlayerGameModeChangeEvent event) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.follow(event.getPlayer()));
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(PlayerDeathEvent event) {
      this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
         this.refresh(event.getEntity());
         Player player = event.getEntity().getKiller();
         if (player != null) {
            this.refresh(player);
         }
      });
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onHidePlayer(PlayerHideEntityEvent event) {
      if (event.getEntity() instanceof Player player) {
         TextDisplay textdisplay = this.displayOf(player);
         if (textdisplay != null) {
            event.getPlayer().hideEntity(this.plugin, textdisplay);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onShowPlayer(PlayerShowEntityEvent event) {
      if (event.getEntity() instanceof Player player) {
         TextDisplay textdisplay = this.displayOf(player);
         if (textdisplay != null) {
            if (!this.hideOwn() || !event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
               event.getPlayer().showEntity(this.plugin, textdisplay);
            }
         }
      }
   }

   private void followAll() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.follow(player);
      }
   }

   private void follow(Player player) {
      if (!this.eligible(player)) {
         this.despawn(player.getUniqueId());
      } else {
         TextDisplay textdisplay = this.displayOf(player);
         if (textdisplay == null || !textdisplay.isValid()) {
            this.spawn(player);
         } else if (this.hidden(player)) {
            textdisplay.setInvisible(true);
         } else {
            textdisplay.setInvisible(false);
            if (!this.ride()) {
               Location location = this.anchor(player);
               if (textdisplay.getWorld() != location.getWorld()) {
                  this.despawn(player.getUniqueId());
                  this.spawn(player);
               } else {
                  Location current = textdisplay.getLocation();
                  if (current.distanceSquared(location) > 0.0004) {
                     textdisplay.teleport(location);
                  }
               }
            } else if (textdisplay.getVehicle() == null || !textdisplay.getVehicle().getUniqueId().equals(player.getUniqueId())) {
               player.addPassenger(textdisplay);
            }
         }
      }
   }

   private void refreshAll() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         this.refresh(player);
      }
   }

   private void refresh(Player player) {
      TextDisplay textdisplay = this.displayOf(player);
      if (textdisplay != null && textdisplay.isValid()) {
         String s = this.renderKey(player);
         if (!s.equals(this.lastText.get(player.getUniqueId()))) {
            this.lastText.put(player.getUniqueId(), s);
            textdisplay.text(this.render(player, s));
         }
      }
   }

   private void spawn(Player player) {
      if (this.eligible(player) && player.getWorld() != null) {
         this.despawn(player.getUniqueId());
         Location location = this.anchor(player);
         if (location.getWorld() != null) {
            try {
               TextDisplay textdisplay = (TextDisplay)location.getWorld()
                  .spawn(
                     location,
                     TextDisplay.class,
                     entity -> {
                        entity.setPersistent(false);
                        entity.setInvulnerable(true);
                        entity.setGravity(false);
                        entity.setBillboard(Billboard.CENTER);
                        entity.setAlignment(TextAlignment.CENTER);
                        entity.setShadowed(this.config.getBoolean("display.shadow", true));
                        entity.setSeeThrough(this.config.getBoolean("display.see-through", false));
                        entity.setDefaultBackground(false);
                        entity.setBackgroundColor(this.parseBackground());
                        entity.setLineWidth(this.config.getInt("display.line-width", 1000));
                        entity.setViewRange((float)(this.config.getDouble("display.view-distance", 32.0) / 64.0));
                        entity.setTeleportDuration(this.ride() ? 0 : this.config.getInt("display.teleport-duration", 2));
                        entity.setInterpolationDuration(0);
                        float f = (float)this.config.getDouble("display.scale", this.config.getDouble("scale", 1.0));
                        entity.setTransformation(
                           new Transformation(new Vector3f(0.0F, (float)this.yOffset(), 0.0F), new Quaternionf(), new Vector3f(f, f, f), new Quaternionf())
                        );
                        entity.setVelocity(new Vector(0, 0, 0));
                        entity.getPersistentDataContainer().set(this.tagKey, PersistentDataType.STRING, player.getUniqueId().toString());
                        String s = this.renderKey(player);
                        this.lastText.put(player.getUniqueId(), s);
                        entity.text(this.render(player, s));
                        if (this.hidden(player)) {
                           entity.setInvisible(true);
                        }
                     }
                  );
               this.displays.put(player.getUniqueId(), textdisplay);
               if (this.ride()) {
                  player.addPassenger(textdisplay);
               }

               this.applyVisibility(player, textdisplay);
            } catch (RuntimeException runtimeexception) {
               this.plugin.getLogger().warning("[nametags] Could not spawn tag for " + player.getName() + ": " + runtimeexception.getMessage());
            }
         }
      }
   }

   private Color parseBackground() {
      String s = this.config.getString("display.background", "#00000000");
      if (s != null && !s.isBlank()) {
         String s1 = s.startsWith("#") ? s.substring(1) : s;

         try {
            if (s1.length() == 8) {
               int i1 = Integer.parseInt(s1.substring(0, 2), 16);
               int j = Integer.parseInt(s1.substring(2, 4), 16);
               int k = Integer.parseInt(s1.substring(4, 6), 16);
               int l = Integer.parseInt(s1.substring(6, 8), 16);
               return Color.fromARGB(i1, j, k, l);
            }

            if (s1.length() == 6) {
               int i = Integer.parseInt(s1, 16);
               return Color.fromARGB(0, i >> 16 & 0xFF, i >> 8 & 0xFF, i & 0xFF);
            }
         } catch (NumberFormatException numberformatexception) {
         }

         return Color.fromARGB(0, 0, 0, 0);
      } else {
         return Color.fromARGB(0, 0, 0, 0);
      }
   }

   private void applyVisibility(Player owner, TextDisplay display) {
      if (this.hideOwn()) {
         owner.hideEntity(this.plugin, display);
      } else {
         owner.showEntity(this.plugin, display);
      }

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!player.getUniqueId().equals(owner.getUniqueId()) && !player.canSee(owner)) {
            player.hideEntity(this.plugin, display);
         }
      }
   }

   private void despawn(UUID ownerId) {
      this.lastText.remove(ownerId);
      TextDisplay textdisplay = this.displays.remove(ownerId);
      if (textdisplay != null) {
         textdisplay.remove();
      }
   }

   private void purgeStrays() {
      if (this.tagKey != null) {
         for (World world : Bukkit.getWorlds()) {
            for (TextDisplay textdisplay : world.getEntitiesByClass(TextDisplay.class)) {
               if (textdisplay.getPersistentDataContainer().has(this.tagKey, PersistentDataType.STRING)) {
                  textdisplay.remove();
               }
            }
         }
      }
   }

   private TextDisplay displayOf(Player player) {
      TextDisplay textdisplay = this.displays.get(player.getUniqueId());
      return textdisplay != null && textdisplay.isValid() ? textdisplay : null;
   }

   private Location anchor(Player player) {
      Entity entity = player;
      if (this.ride() && player.getVehicle() != null) {
         entity = player.getVehicle();
      }

      double d0 = this.yOffset();
      if (player.isSneaking() && this.config.getBoolean("display.hide-when-sneaking", true)) {
         d0 -= 0.35;
      }

      Location location = entity.getLocation().clone().add(0.0, entity.getHeight() + d0, 0.0);
      location.setPitch(0.0F);
      location.setYaw(0.0F);
      return location;
   }

   private double yOffset() {
      return this.config.getDouble("display.y-offset", this.config.getDouble("height", 0.35));
   }

   private boolean ride() {
      return this.config.getBoolean("display.ride", this.config.getBoolean("ride", true));
   }

   private boolean eligible(Player player) {
      if (player == null || !player.isOnline() || player.hasMetadata("NPC")) {
         return false;
      } else if (!this.config.getBoolean("enabled", true)) {
         return false;
      } else {
         List<String> list = this.config.getStringList("disabled-worlds");
         if (!list.isEmpty() && list.stream().anyMatch(w -> w.equalsIgnoreCase(player.getWorld().getName()))) {
            return false;
         } else {
            List<String> list1 = this.config.getStringList("enabled-worlds");
            return list1.isEmpty() || list1.contains(player.getWorld().getName());
         }
      }
   }

   private boolean hidden(Player player) {
      if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
         return true;
      } else {
         return this.config.getBoolean("display.hide-when-invisible", true) && player.isInvisible()
            ? true
            : this.config.getBoolean("display.hide-when-sneaking", false) && player.isSneaking();
      }
   }

   private Component render(Player player) {
      return this.render(player, this.renderKey(player));
   }

   private Component render(Player player, String key) {
      TextComponent textcomponent = Component.empty();
      String[] astring = key.split("\n", -1);
      boolean flag = true;

      for (String s : astring) {
         if (!flag) {
            textcomponent = (TextComponent)textcomponent.append(Component.newline());
         }

         flag = false;
         textcomponent = (TextComponent)textcomponent.append(Text.c(s));
      }

      return textcomponent;
   }

   private String renderKey(Player player) {
      List<String> list = this.currentRows(player);
      StringBuilder stringbuilder = new StringBuilder();

      for (int i = 0; i < list.size(); i++) {
         if (i > 0) {
            stringbuilder.append('\n');
         }

         stringbuilder.append(this.apply(player, list.get(i)));
      }

      return stringbuilder.toString();
   }

   private List<String> currentRows(Player player) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("lines");
      if (configurationsection == null) {
         return List.of(
            "%luckperms_prefix%&f%player_name%%shardedcore_tag%",
            "&#FF0000\ud83d\udde1 &#FF0000%statistic_player_kills% &7| &#FB7B0B☠ &#FB7B0B%statistic_deaths% &7| &#FFF300⌛ &#FFF300%statistic_time_played%"
         );
      } else {
         List<String> list = new ArrayList<>();

         for (String s : List.of("top", "bottom")) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null && configurationsection1.getBoolean("enabled", true)) {
               List<String> list1 = configurationsection1.getStringList("frames");
               if (!list1.isEmpty()) {
                  list.add(this.pickFrame(configurationsection1, list1));
               }
            }
         }

         if (list.isEmpty()) {
            List<String> list2 = this.config.getStringList("lines");
            if (!list2.isEmpty()) {
               return list2;
            }
         }

         return list;
      }
   }

   private String pickFrame(ConfigurationSection section, List<String> frames) {
      if (frames.size() == 1) {
         return frames.getFirst();
      } else {
         long i = Math.max(0L, section.getLong("interval", 80L));
         if (i <= 0L) {
            return frames.getFirst();
         } else {
            long j = Math.max(20L, section.getLong("refresh", this.config.getLong("refresh", 20L)));
            long k = Math.max(j, (i + j - 1L) / j * j);
            int l = (int)(this.tick.get() * j / k % (long)frames.size());
            return frames.get(Math.max(0, Math.min(frames.size() - 1, l)));
         }
      }
   }

   private String apply(Player player, String line) {
      String s = player.getName();
      if (this.plugin.cosmetics() != null) {
         try {
            String s1 = this.plugin.cosmetics().formattedName(player);
            if (s1 != null && !s1.isBlank()) {
               s = s1;
            }
         } catch (Throwable throwable) {
         }
      }

      String s4 = line.replace("%colored_name%", s)
         .replace("%player_name%", s)
         .replace("%player%", player.getName())
         .replace("%shardedcore_money_formatted%", "%shardedcore_tokens_formatted%")
         .replace("%shardedcore_crystals_formatted%", "%shardedcore_tokens_formatted%");
      s4 = PlaceholderUtil.apply(player, s4);
      if (s4.contains("%shardedcore_tokens_formatted%")) {
         s4 = s4.replace("%shardedcore_tokens_formatted%", "%token_formatted%");
         s4 = PlaceholderUtil.apply(player, s4);
      }

      s4 = s4.replace("%statistic_player_kills%", String.valueOf(player.getStatistic(Statistic.PLAYER_KILLS)));
      s4 = s4.replace("%statistic_deaths%", String.valueOf(player.getStatistic(Statistic.DEATHS)));
      if (s4.contains("%statistic_time_played%")) {
         s4 = s4.replace("%statistic_time_played%", this.formatPlaytime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)));
      }

      if (s4.contains("%shardedcore_team%")) {
         s4 = s4.replace("%shardedcore_team%", this.teamName(player));
      }

      if (s4.contains("%shardedcore_elo%")) {
         s4 = s4.replace("%shardedcore_elo%", this.eloDisplay(player));
      }

      if (s4.contains("%luckperms_prefix%") || s4.contains("%shardedcore_rank%") || s4.contains("%luckperms_suffix%")) {
         String s2 = this.rankDisplay(player);
         String s3 = this.suffixDisplay(player);
         s4 = s4.replace("%luckperms_prefix%", s2);
         s4 = s4.replace("%shardedcore_rank%", s2);
         s4 = s4.replace("%luckperms_primary_group%", s2);
         s4 = s4.replace("%vault_prefix%", s2);
         s4 = s4.replace("%luckperms_suffix%", s3);
      }

      if (s4.contains("%shardedcore_tag%")) {
         s4 = s4.replace("%shardedcore_tag%", this.equippedTag(player));
      }

      return s4;
   }

   private String eloDisplay(Player player) {
      try {
         DuelModule duelmodule = this.plugin.modules().get(DuelModule.class);
         return duelmodule == null ? "1000" : String.valueOf(duelmodule.rating(player.getUniqueId(), player.getName()));
      } catch (Throwable throwable) {
         return "1000";
      }
   }

   private String equippedTag(Player player) {
      if (this.plugin.cosmetics() == null) {
         return "";
      } else {
         try {
            String s = this.plugin.cosmetics().tagDisplay(player.getUniqueId());
            return s == null ? "" : s;
         } catch (Throwable throwable) {
            return "";
         }
      }
   }

   private String formatPlaytime(int ticks) {
      long i = Math.max(0L, (long)ticks / 20L);
      long j = i / 3600L;
      long k = i % 3600L / 60L;
      long l = i % 60L;
      if (j > 0L) {
         return j + "h " + k + "m " + l + "s";
      } else {
         return k > 0L ? k + "m " + l + "s" : l + "s";
      }
   }

   private String teamName(Player player) {
      TeamsModule teamsmodule = this.plugin.modules().get(TeamsModule.class);
      if (teamsmodule != null && teamsmodule.database() != null) {
         Integer integer = teamsmodule.database().getTeamId(player.getUniqueId());
         if (integer == null) {
            return teamsmodule.notInTeamPlaceholder();
         } else {
            TeamDatabase.Team teamdatabase$team = teamsmodule.database().getTeamById(integer);
            return teamdatabase$team == null ? teamsmodule.notInTeamPlaceholder() : teamdatabase$team.name();
         }
      } else {
         return "None";
      }
   }

   private String rankDisplay(Player player) {
      if (this.plugin.luckPerms() != null) {
         String s = this.plugin.luckPerms().prefix(player);
         if (s != null && !s.isBlank()) {
            return s.trim();
         }

         String s1 = this.plugin.luckPerms().primaryGroup(player);
         if (s1 != null && !s1.isBlank()) {
            return Text.pretty(s1);
         }
      }

      return "";
   }

   private String suffixDisplay(Player player) {
      if (this.plugin.luckPerms() != null) {
         try {
            Method method = this.plugin.luckPerms().getClass().getMethod("suffix", Player.class);
            Object object = method.invoke(this.plugin.luckPerms(), player);
            return object == null ? "" : String.valueOf(object).trim();
         } catch (ReflectiveOperationException reflectiveoperationexception) {
         }
      }

      return "";
   }

   private boolean hideOwn() {
      return this.config.getBoolean("display.hide-own", this.config.getBoolean("hide-own", false));
   }

   private void clampRefresh() {
      boolean flag = false;
      if (this.config.getLong("refresh", 20L) < 20L) {
         this.config.set("refresh", 20L);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 4) {
         this.config.set("config-version", 4);
         flag = true;
      }

      if (flag) {
         try {
            this.config.save(new File(this.moduleFolder(), "config.yml"));
         } catch (Exception exception) {
         }
      }
   }
}

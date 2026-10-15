package com.sharded.core.modules.collisions;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public final class CollisionsModule extends Module {
   private static final String TEAM_NAME = "sc_nocollide";
   private NamespacedKey petKey;
   private Team noCollideTeam;
   private BukkitTask refreshTask;

   public CollisionsModule(ShardedCore plugin) {
      super(plugin, "collisions");
   }

   @Override
   protected void onEnable() {
      this.petKey = new NamespacedKey(this.plugin, "pet_owner");
      this.setupTeamDeferred();

      for (Player player : Bukkit.getOnlinePlayers()) {
         this.applyPlayerCollision(player);
      }

      long i = Math.max(40L, this.config.getLong("refresh-interval-ticks", 100L));
      this.refreshTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
         if (this.noCollideTeam == null) {
            this.setupTeamDeferred();
         }

         for (Player player1 : Bukkit.getOnlinePlayers()) {
            if (this.noCollideTeam == null || !this.noCollideTeam.hasEntry(player1.getUniqueId().toString())) {
               this.applyPlayerCollision(player1);
            }
         }
      }, i, i);
   }

   @Override
   protected void onDisable() {
      if (this.refreshTask != null) {
         this.refreshTask.cancel();
      }

      this.refreshTask = null;
   }

   private void setupTeamDeferred() {
      ScoreboardManager scoreboardmanager = Bukkit.getScoreboardManager();
      if (scoreboardmanager != null) {
         Scoreboard scoreboard = scoreboardmanager.getMainScoreboard();
         Team team = scoreboard.getTeam("sc_nocollide");
         if (team == null) {
            team = scoreboard.registerNewTeam("sc_nocollide");
         }

         team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
         team.setCanSeeFriendlyInvisibles(true);
         this.noCollideTeam = team;
      }
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      if (this.noCollideTeam == null) {
         this.setupTeamDeferred();
      }

      this.applyPlayerCollision(event.getPlayer());
   }

   @EventHandler
   public void onEntitySpawn(EntitySpawnEvent event) {
      if (this.isPet(event.getEntity())) {
         this.applyEntityCollision(event.getEntity());
      }
   }

   public void applyPlayerCollision(Player player) {
      if (this.config.getBoolean("disable-player-collisions", true)) {
         player.setCollidable(false);
         if (this.noCollideTeam != null) {
            String s = player.getUniqueId().toString();
            if (!this.noCollideTeam.hasEntry(s)) {
               this.noCollideTeam.addEntry(s);
            }

            if (!this.noCollideTeam.hasEntry(player.getName())) {
               this.noCollideTeam.addEntry(player.getName());
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH,
      ignoreCancelled = true
   )
   public void onEntityPush(EntityPushedByEntityAttackEvent event) {
      if (this.config.getBoolean("disable-player-collisions", true)) {
         if (event.getEntity() instanceof Player && event.getPushedBy() instanceof Player) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onSwapHands(PlayerSwapHandItemsEvent event) {
      if (this.config.getBoolean("disable-player-collisions", true)) {
         this.applyPlayerCollision(event.getPlayer());
      }
   }

   public void applyEntityCollision(Entity entity) {
      if (this.config.getBoolean("disable-pet-collisions", true)) {
         if (entity instanceof LivingEntity livingentity) {
            livingentity.setCollidable(false);
         }

         if (this.noCollideTeam != null) {
            String s = entity.getUniqueId().toString();
            if (!this.noCollideTeam.hasEntry(s)) {
               this.noCollideTeam.addEntry(s);
            }
         }
      }
   }

   boolean isPet(Entity entity) {
      return entity.getPersistentDataContainer().has(this.petKey, PersistentDataType.STRING);
   }

   NamespacedKey petKey() {
      return this.petKey;
   }
}

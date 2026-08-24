package com.sharded.core.modules.collisions;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import org.bukkit.scheduler.BukkitTask;

/** Disables player-to-player and pet collisions. */
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
        petKey = new NamespacedKey(plugin, "pet_owner");
        setupTeamDeferred();
        for (Player player : Bukkit.getOnlinePlayers()) {
            applyPlayerCollision(player);
        }
        long interval = Math.max(40L, config.getLong("refresh-interval-ticks", 100L));
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (noCollideTeam == null) setupTeamDeferred();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (noCollideTeam != null && noCollideTeam.hasEntry(player.getUniqueId().toString())) continue;
                applyPlayerCollision(player);
            }
        }, interval, interval);
    }

    @Override
    protected void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        refreshTask = null;
    }

    private void setupTeamDeferred() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getMainScoreboard();
        Team team = board.getTeam(TEAM_NAME);
        if (team == null) team = board.registerNewTeam(TEAM_NAME);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(true);
        noCollideTeam = team;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (noCollideTeam == null) setupTeamDeferred();
        applyPlayerCollision(event.getPlayer());
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (isPet(event.getEntity())) applyEntityCollision(event.getEntity());
    }

    public void applyPlayerCollision(Player player) {
        if (!config.getBoolean("disable-player-collisions", true)) return;
        if (player.isCollidable()) player.setCollidable(false);
        if (noCollideTeam != null) {
            String entry = player.getUniqueId().toString();
            if (!noCollideTeam.hasEntry(entry)) {
                noCollideTeam.addEntry(entry);
            }
            if (!noCollideTeam.hasEntry(player.getName())) {
                noCollideTeam.addEntry(player.getName());
            }
        }
    }

    public void applyEntityCollision(Entity entity) {
        if (!config.getBoolean("disable-pet-collisions", true)) return;
        if (entity instanceof LivingEntity living) {
            living.setCollidable(false);
        }
        if (noCollideTeam != null) {
            String entry = entity.getUniqueId().toString();
            if (!noCollideTeam.hasEntry(entry)) noCollideTeam.addEntry(entry);
        }
    }

    boolean isPet(Entity entity) {
        return entity.getPersistentDataContainer().has(petKey, PersistentDataType.STRING);
    }

    NamespacedKey petKey() {
        return petKey;
    }
}

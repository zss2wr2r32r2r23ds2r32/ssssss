package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.util.Feedback;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Owns the per-mode spawn points and every teleport back to them.
 *
 * <p>Join placement is handled through {@link AsyncPlayerSpawnLocationEvent}
 * instead of a delayed teleport after login. That way the client only ever loads
 * the chunks around the event spawn, rather than streaming the player's last
 * logout area and immediately throwing it away.</p>
 */
public final class SpawnModule extends EventModule {

    /**
     * Join placement runs during the configuration phase, off the main thread,
     * so the destination and the spread settings are cached here rather than
     * read from the live configuration on that thread.
     */
    private volatile Location joinSpawn;
    private volatile boolean joinSpawnEnabled;
    private volatile boolean spreadEnabled;
    private volatile double spreadRadius;

    public SpawnModule(ShardedEventCore plugin) {
        super(plugin, "spawn", "Per-mode spawn points, /setspawn and /spawn.");
    }

    @Override
    protected void onModuleEnable() {
        refreshCache();
    }

    @Override
    protected void onConfigReload() {
        refreshCache();
    }

    /**
     * Re-reads the values the async join handler relies on. Called whenever the
     * spawn, the selected gamemode or the configuration changes.
     */
    public void refreshCache() {
        joinSpawnEnabled = config().raw().getBoolean("teleport-on-join", true);
        spreadEnabled = config().raw().getBoolean("spread.enabled", false);
        spreadRadius = Math.max(0.0D, config().raw().getDouble("spread.radius", 3.0D));
        Location resolved = resolveActiveSpawn();
        joinSpawn = resolved == null ? null : resolved.clone();
    }

    // ------------------------------------------------------------------ setup

    /** Stores {@code location} as the spawn for {@code mode} and lifts the whitelist. */
    public void setSpawn(EventMode mode, Location location) {
        Location stored = config().raw().getBoolean("centre-on-block", true)
                ? centre(location)
                : location.clone();
        plugin.state().setSpawn(mode, stored);
        refreshCache();

        if (config().raw().getBoolean("disable-whitelist-on-setspawn", true)) {
            setWhitelist(false);
        }
    }

    private static Location centre(Location location) {
        Location centred = location.clone();
        centred.setX(location.getBlockX() + 0.5D);
        centred.setZ(location.getBlockZ() + 0.5D);
        return centred;
    }

    public void setWhitelist(boolean value) {
        Bukkit.setWhitelist(value);
        if (config().raw().getBoolean("enforce-whitelist", false)) {
            Bukkit.setWhitelistEnforced(value);
        }
        Bukkit.reloadWhitelist();
    }

    // -------------------------------------------------------------- teleports

    public Location resolveSpawn(EventMode mode) {
        Location spawn = plugin.state().spawn(mode);
        if (spawn != null) {
            return spawn;
        }
        if (!config().raw().getBoolean("fall-back-to-world-spawn", true)) {
            return null;
        }
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        return world == null ? null : world.getSpawnLocation();
    }

    /** Spawn of the currently selected mode, falling back to the world spawn. */
    public Location resolveActiveSpawn() {
        EventMode mode = plugin.state().selected();
        return mode == null ? null : resolveSpawn(mode);
    }

    public void teleport(Player player, Location target) {
        if (target == null) {
            return;
        }
        player.teleportAsync(scatter(target));
        Feedback.play(player, Feedback.sound(config().raw().getConfigurationSection("sound")));
    }

    /**
     * Teleports a whole group. The destination chunk is warmed once up front so
     * the moves do not each trigger a synchronous chunk load.
     */
    public int teleportAll(Collection<? extends Player> players, Location target) {
        if (target == null || players.isEmpty()) {
            return 0;
        }
        World world = target.getWorld();
        if (world != null) {
            world.getChunkAtAsync(target, true);
        }
        Sound sound = Feedback.sound(config().raw().getConfigurationSection("sound"));
        int moved = 0;
        for (Player player : players) {
            player.teleportAsync(scatter(target));
            Feedback.play(player, sound);
            moved++;
        }
        return moved;
    }

    public int teleportEveryone(Location target) {
        return teleportAll(Bukkit.getOnlinePlayers(), target);
    }

    /** Optional random offset so a big group does not land in one block. */
    private Location scatter(Location target) {
        double radius = spreadRadius;
        if (!spreadEnabled || radius <= 0.0D) {
            return target;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location scattered = target.clone();
        scattered.add(random.nextDouble(-radius, radius), 0.0D, random.nextDouble(-radius, radius));
        return scattered;
    }

    // ----------------------------------------------------------------- events

    /**
     * Places a joining player at the event spawn before their world is streamed,
     * so the client never loads the chunks around their last logout point.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onSpawnLocation(AsyncPlayerSpawnLocationEvent event) {
        if (!joinSpawnEnabled) {
            return;
        }
        Location spawn = joinSpawn;
        if (spawn != null) {
            event.setSpawnLocation(scatter(spawn));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!config().raw().getBoolean("teleport-on-respawn", true)) {
            return;
        }
        Location spawn = resolveActiveSpawn();
        if (spawn != null) {
            event.setRespawnLocation(scatter(spawn));
        }
    }
}

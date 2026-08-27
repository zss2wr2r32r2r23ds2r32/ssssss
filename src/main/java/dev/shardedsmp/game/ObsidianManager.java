package dev.shardedsmp.game;

import dev.shardedsmp.ShardedSMP;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

public class ObsidianManager {
    private final ShardedSMP plugin;
    private BukkitTask pendingTask;

    public ObsidianManager(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    public void scheduleAfterGrace() {
        GameManager game = plugin.game();
        if (!game.graceStarted() || game.graceActive()) {
            return;
        }
        if (game.obsidianSpawned() >= game.obsidianTotal()) {
            return;
        }
        cancel();
        int min;
        int max;
        if (game.obsidianSpawned() == 0) {
            min = plugin.getConfig().getInt("obsidian.first-delay-min-seconds", 90);
            max = plugin.getConfig().getInt("obsidian.first-delay-max-seconds", 240);
        } else {
            min = plugin.getConfig().getInt("obsidian.delay-min-seconds", 80);
            max = plugin.getConfig().getInt("obsidian.delay-max-seconds", 280);
        }
        int delaySeconds = randomBetween(min, max);
        pendingTask = Bukkit.getScheduler().runTaskLater(plugin, this::trySpawn, delaySeconds * 20L);
        plugin.getLogger().info("Next obsidian spawn scheduled in " + delaySeconds + " seconds.");
    }

    public void trySpawn() {
        GameManager game = plugin.game();
        if (game.obsidianSpawned() >= game.obsidianTotal()) {
            return;
        }
        if (game.graceActive()) {
            scheduleAfterGrace();
            return;
        }
        if (!game.canStartObsidianSpawns()) {
            pendingTask = Bukkit.getScheduler().runTaskLater(plugin, this::trySpawn, 20L * 30);
            return;
        }
        spawnOne(true);
        if (game.obsidianSpawned() < game.obsidianTotal()) {
            scheduleAfterGrace();
        }
    }

    public boolean spawnOne(boolean countTowardTotal) {
        GameManager game = plugin.game();
        World world = game.overworld();
        if (world == null) {
            return false;
        }
        int pieceId = game.nextPieceId();
        if (!countTowardTotal) {
            pieceId = 1000 + ThreadLocalRandom.current().nextInt(9000);
        } else if (pieceId > game.obsidianTotal()) {
            return false;
        }
        Location drop = randomSkyLocation(world);
        game.spawnObsidianItem(drop, pieceId, countTowardTotal);
        return true;
    }

    public boolean spawnTest(Player player) {
        GameManager game = plugin.game();
        World world = player != null ? player.getWorld() : game.overworld();
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            world = game.overworld();
        }
        if (world == null) {
            return false;
        }
        boolean count = game.obsidianSpawned() < game.obsidianTotal() && game.graceStarted() && !game.graceActive();
        int pieceId = count ? game.nextPieceId() : 9000 + ThreadLocalRandom.current().nextInt(1000);
        Location drop;
        if (player != null) {
            drop = player.getLocation().clone().add(0, 12, 0);
        } else {
            drop = randomSkyLocation(world);
        }
        game.spawnObsidianItem(drop, pieceId, count);
        return true;
    }

    public void cancel() {
        if (pendingTask != null) {
            pendingTask.cancel();
            pendingTask = null;
        }
    }

    private Location randomSkyLocation(World world) {
        Location safe = dev.shardedsmp.util.LocationUtil.randomSafeLocation(world, plugin.game().borderPadding(), 40);
        double skyY = Math.min(world.getMaxHeight() - 2, Math.max(safe.getY() + 35, world.getMaxHeight() - 16));
        return new Location(world, safe.getX(), skyY, safe.getZ());
    }

    private int randomBetween(int min, int max) {
        if (max < min) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}

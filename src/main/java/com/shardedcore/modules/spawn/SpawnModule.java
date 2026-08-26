package com.shardedcore.modules.spawn;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpawnModule extends Module implements CommandExecutor, Listener {

    private final Map<UUID, BukkitTask> pending = new ConcurrentHashMap<>();
    private File spawnFile;
    private Location spawn;

    public SpawnModule(ShardedCore plugin) {
        super(plugin, "spawn");
    }

    @Override
    public void enable() {
        spawnFile = new File(folder, "spawn.yml");
        loadSpawn();
        registerCommand("spawn", this);
        registerCommand("setspawn", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        pending.values().forEach(BukkitTask::cancel);
        pending.clear();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (command.getName().equalsIgnoreCase("setspawn")) {
            if (!player.hasPermission("shardedcore.spawn.admin")) {
                sendRaw(player, "&#FF0000&lERROR &7▷ &fYou do not have permission.");
                return true;
            }
            spawn = player.getLocation();
            saveSpawn();
            send(player, "set");
            return true;
        }
        if (spawn == null) {
            send(player, "missing");
            return true;
        }
        start(player);
        return true;
    }

    private void start(Player player) {
        if (pending.containsKey(player.getUniqueId())) {
            send(player, "already");
            return;
        }
        int seconds = config.getInt("countdown-seconds", 5);
        int[] left = {seconds};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancel(player.getUniqueId());
                return;
            }
            if (left[0] <= 0) {
                cancel(player.getUniqueId());
                player.teleportAsync(spawn);
                Sounds.play(player, cfg("sound", "entity.enderman.teleport"), 1f, 1f);
                send(player, "teleported");
                return;
            }
            player.sendActionBar(ColorUtil.parse(cfg("prefix", "").replace("%seconds%", String.valueOf(left[0]))));
            left[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), task);
    }

    private void cancel(UUID uuid) {
        BukkitTask task = pending.remove(uuid);
        if (task != null) task.cancel();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!config.getBoolean("cancel-on-move", true)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (!pending.containsKey(event.getPlayer().getUniqueId())) return;
        cancel(event.getPlayer().getUniqueId());
        send(event.getPlayer(), "cancelled");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId());
    }

    private void loadSpawn() {
        if (!spawnFile.exists()) return;
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(spawnFile);
        World world = Bukkit.getWorld(yaml.getString("world", "world"));
        if (world == null) return;
        spawn = new Location(world, yaml.getDouble("x"), yaml.getDouble("y"), yaml.getDouble("z"),
                (float) yaml.getDouble("yaw"), (float) yaml.getDouble("pitch"));
    }

    private void saveSpawn() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", spawn.getWorld().getName());
        yaml.set("x", spawn.getX());
        yaml.set("y", spawn.getY());
        yaml.set("z", spawn.getZ());
        yaml.set("yaw", spawn.getYaw());
        yaml.set("pitch", spawn.getPitch());
        Configs.save(yaml, spawnFile);
    }
}

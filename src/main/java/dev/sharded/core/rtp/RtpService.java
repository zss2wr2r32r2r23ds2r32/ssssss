package dev.sharded.core.rtp;

import dev.sharded.core.ShardedCore;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class RtpService implements Listener, CommandExecutor {
    private static final Set<Material> UNSAFE = Set.of(
            Material.LAVA, Material.WATER, Material.CACTUS, Material.MAGMA_BLOCK,
            Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.KELP, Material.SEAGRASS
    );

    private final ShardedCore plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public RtpService(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.messages().get("core.players-only"));
            return true;
        }
        if (!player.hasPermission("shardedcore.rtp")) {
            player.sendMessage(plugin.messages().get("portalrtp.no-permission"));
            return true;
        }
        randomTeleport(player, "command");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (!plugin.getConfig().getBoolean("portal-rtp.enabled", true)) {
            return;
        }
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.getConfig().getStringList("portal-rtp.source-worlds").contains(player.getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> randomTeleport(player, "portal"));
    }

    public void randomTeleport(Player player, String source) {
        if (!player.hasPermission("shardedcore.rtp")) {
            player.sendMessage(plugin.messages().get("portalrtp.no-permission"));
            return;
        }
        String prefix = source.equals("portal") ? "portal-rtp" : "rtp";
        int cooldown = plugin.getConfig().getInt(prefix + ".cooldown-seconds", 8);
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldown * 1000L) {
            int remain = (int) Math.ceil((cooldown * 1000L - (now - last)) / 1000.0);
            player.sendMessage(plugin.messages().get("portalrtp.cooldown", "%seconds%", String.valueOf(remain)));
            return;
        }

        String destPath = source.equals("portal") ? "portal-rtp.destination.world" : "rtp.world";
        String worldName = plugin.getConfig().getString(destPath, "world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            // This is the key players were seeing as <missing message: portalrtp/unknown-destination>
            player.sendMessage(plugin.messages().get("portalrtp/unknown-destination"));
            return;
        }

        cooldowns.put(player.getUniqueId(), now);
        player.sendMessage(plugin.messages().get("portalrtp.searching"));
        int min = plugin.getConfig().getInt(source.equals("portal") ? "portal-rtp.destination.min-radius" : "rtp.min-radius", 250);
        int max = plugin.getConfig().getInt(source.equals("portal") ? "portal-rtp.destination.max-radius" : "rtp.max-radius", 2500);
        int cx = plugin.getConfig().getInt(source.equals("portal") ? "portal-rtp.destination.center-x" : "rtp.center-x", 0);
        int cz = plugin.getConfig().getInt(source.equals("portal") ? "portal-rtp.destination.center-z" : "rtp.center-z", 0);
        int tries = plugin.getConfig().getInt(source.equals("portal") ? "portal-rtp.destination.max-tries" : "rtp.max-tries", 24);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Location found = findSafe(world, cx, cz, min, max, tries);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (found == null) {
                    cooldowns.remove(player.getUniqueId());
                    player.sendMessage(plugin.messages().get("portalrtp.failed"));
                    return;
                }
                player.teleportAsync(found).thenAccept(ok -> {
                    if (Boolean.TRUE.equals(ok)) {
                        player.sendMessage(plugin.messages().get("portalrtp.success"));
                    } else {
                        player.sendMessage(plugin.messages().get("portalrtp.failed"));
                    }
                });
            });
        });
    }

    private Location findSafe(World world, int cx, int cz, int min, int max, int tries) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < tries; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int radius = random.nextInt(min, Math.max(min + 1, max + 1));
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int z = cz + (int) Math.round(Math.sin(angle) * radius);
            world.getChunkAt(x >> 4, z >> 4);
            Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (!ground.getType().isSolid() || UNSAFE.contains(ground.getType()) || Tag.LEAVES.isTagged(ground.getType())) {
                continue;
            }
            Block above = ground.getRelative(0, 1, 0);
            Block head = ground.getRelative(0, 2, 0);
            if (!above.isPassable() || !head.isPassable() || UNSAFE.contains(above.getType())) {
                continue;
            }
            Location loc = above.getLocation().add(0.5, 0, 0.5);
            loc.setYaw(random.nextInt(360));
            if (plugin.spawns().isInSpawnRegion(loc)) {
                continue;
            }
            return loc;
        }
        return null;
    }
}

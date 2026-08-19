package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.SafeLocationFinder;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalRtpModule extends Module implements CommandExecutor {

    private PortalTriggerStore triggers;
    private final Map<UUID, Long> portalGuiCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rtpCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();

    private record PendingTeleport(Location start, BukkitTask task) {
    }

    public PortalRtpModule(ShardedCore plugin) {
        super(plugin, "portalrtp");
    }

    @Override
    protected void onEnable() {
        registerCommand("rtp", this);
        triggers = new PortalTriggerStore(plugin, moduleFolder());

        File guiFile = new File(moduleFolder(), "gui.yml");
        ConfigSync.sync(plugin, guiFile, "modules/portalrtp/gui.yml");
        plugin.gui().loadMenu(guiFile, "portalrtp");
        plugin.gui().registerAction("rtp_confirm", this::startCountdown);
    }

    @Override
    protected void onDisable() {
        for (UUID uuid : pending.keySet()) {
            cancelPending(uuid, false);
        }
        pending.clear();
    }

    private String portalWorld() {
        return config.getString("portal-world", "spawn");
    }

    private String targetWorld() {
        return config.getString("target-world", "world");
    }

    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (event.getEntity() instanceof Player player) {
            Location loc = event.getLocation().getBlock().getLocation();
            if (onTriggerBlock(loc)) tryOpen(player, loc);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Location to = event.getTo();
        if (to == null) return;
        PendingTeleport pt = pending.get(event.getPlayer().getUniqueId());
        if (pt != null && moved(pt.start(), to)) {
            cancelPending(event.getPlayer().getUniqueId(), true);
            return;
        }
        if (onTriggerBlock(to)) {
            tryOpen(event.getPlayer(), to.getBlock().getLocation());
        }
    }

    private boolean moved(Location from, Location to) {
        return from.distanceSquared(to) > 0.01;
    }

    private void tryOpen(Player player, Location at) {
        if (!player.getWorld().getName().equalsIgnoreCase(portalWorld())) return;
        if (!player.hasPermission("sharded.rtp.use")) return;
        if (!triggers.isTrigger(at)) return;
        long now = System.currentTimeMillis();
        Long last = portalGuiCooldown.get(player.getUniqueId());
        if (last != null && now - last < config.getLong("gui-cooldown-ms", 2000L)) return;
        portalGuiCooldown.put(player.getUniqueId(), now);
        openGui(player);
    }

    private boolean onTriggerBlock(Location location) {
        if (location == null || location.getWorld() == null) return false;
        Location block = location.getBlock().getLocation();
        if (triggers.isTrigger(block)) return true;
        return triggers.isTrigger(block.clone().subtract(0, 1, 0));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getName().equalsIgnoreCase(portalWorld())) {
            event.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.rtp.use")) {
            send(player, "no-permission");
            return true;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(portalWorld())) {
            send(player, "wrong-world", "%world%", portalWorld());
            return true;
        }
        openGui(player);
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId(), false);
        portalGuiCooldown.remove(event.getPlayer().getUniqueId());
    }

    private void openGui(Player player) {
        Map<String, String> ph = Map.of(
                "target_world", targetWorld(),
                "radius", config.getString("radius-label", "50K x 50K"),
                "border", config.getString("border-label", "100K x 100K"),
                "players_online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        plugin.gui().open(player, "portalrtp", ph);
    }

    private void startCountdown(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(portalWorld())) {
            send(player, "wrong-world", "%world%", portalWorld());
            return;
        }
        long cooldownSeconds = config.getLong("cooldown-seconds", 30L);
        if (!player.hasPermission("sharded.rtp.bypass")) {
            Long nextUse = rtpCooldown.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (nextUse != null && nextUse > now) {
                send(player, "on-cooldown", "%time%", Text.time((nextUse - now) / 1000L));
                return;
            }
        }
        cancelPending(player.getUniqueId(), false);
        int seconds = config.getInt("countdown-seconds", 5);
        Location start = player.getLocation().clone();
        player.closeInventory();

        int[] remaining = {seconds};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelPending(player.getUniqueId(), false);
                return;
            }
            if (remaining[0] <= 0) {
                cancelPending(player.getUniqueId(), false);
                finishTeleport(player, cooldownSeconds);
                return;
            }
            String msg = raw("countdown", "%seconds%", String.valueOf(remaining[0]));
            MessageUtil.deliver(player, msg, resolveDelivery("countdown"));
            remaining[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), new PendingTeleport(start, task));
    }

    private void cancelPending(UUID uuid, boolean notify) {
        PendingTeleport pt = pending.remove(uuid);
        if (pt != null) pt.task().cancel();
        if (notify) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) send(player, "countdown-cancelled");
        }
    }

    private void finishTeleport(Player player, long cooldownSeconds) {
        World world = Bukkit.getWorld(targetWorld());
        if (world == null) {
            send(player, "world-not-found", "%world%", targetWorld());
            return;
        }
        Location location = findSafeLocation(world);
        if (location == null) {
            send(player, "no-safe-location");
            return;
        }
        if (!player.hasPermission("sharded.rtp.bypass")) {
            rtpCooldown.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            if (success) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1f);
                send(player, "teleported",
                        "%x%", String.valueOf(location.getBlockX()),
                        "%y%", String.valueOf(location.getBlockY()),
                        "%z%", String.valueOf(location.getBlockZ()),
                        "%world%", world.getName());
            }
        });
    }

    public String targetWorldName() {
        return targetWorld();
    }

    /** Finds a safe random location using this module's RTP settings. */
    public Location findSafeLocation(World world) {
        return SafeLocationFinder.find(world, config);
    }
}

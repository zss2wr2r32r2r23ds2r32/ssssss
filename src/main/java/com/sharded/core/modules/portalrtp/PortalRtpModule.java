package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import com.sharded.core.gui.GuiListener;
import com.sharded.core.gui.GuiManager;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Portal RTP - nether portal in factions opens a configurable GUI, then RTP into world. */
public final class PortalRtpModule extends Module implements CommandExecutor {

    private GuiManager guiManager;
    private final Map<UUID, Long> portalGuiCooldown = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();

    public PortalRtpModule(ShardedCore plugin) {
        super(plugin, "portalrtp");
    }

    @Override
    protected void onEnable() {
        registerCommand("rtp", this);
        guiManager = new GuiManager(plugin);
        File guiFile = new File(moduleFolder(), "gui.yml");
        if (!guiFile.exists()) plugin.saveResource("modules/portalrtp/gui.yml", false);
        guiManager.loadFolder(moduleFolder());
        guiManager.registerAction("rtp_confirm", this::teleport);
        registerListener(new GuiListener(guiManager));
    }

    private String portalWorld() {
        return config.getString("portal-world", "factions");
    }

    private String targetWorld() {
        return config.getString("target-world", "world");
    }

    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        tryOpen(player);
    }

    /** Backup detection when standing inside portal blocks. */
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Location to = event.getTo();
        if (to == null) return;
        if (to.getBlock().getType() != Material.NETHER_PORTAL
                && to.clone().subtract(0, 1, 0).getBlock().getType() != Material.NETHER_PORTAL) return;
        tryOpen(event.getPlayer());
    }

    private void tryOpen(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase(portalWorld())) return;
        if (!player.hasPermission("sharded.rtp.use")) return;
        long now = System.currentTimeMillis();
        Long last = portalGuiCooldown.get(player.getUniqueId());
        if (last != null && now - last < config.getLong("gui-cooldown-ms", 2000L)) return;
        portalGuiCooldown.put(player.getUniqueId(), now);
        openGui(player);
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
        if (!player.getWorld().getName().equalsIgnoreCase(portalWorld())
                && !player.hasPermission("sharded.rtp.bypass")) {
            send(player, "wrong-world", "%world%", portalWorld());
            return true;
        }
        openGui(player);
        return true;
    }

    private void openGui(Player player) {
        Map<String, String> ph = Map.of(
                "target_world", targetWorld(),
                "border", config.getString("border-label", "5K x 5K"));
        guiManager.open(player, "gui", ph);
    }

    private void teleport(Player player) {
        long cooldownSeconds = config.getLong("cooldown-seconds", 30L);
        if (!player.hasPermission("sharded.rtp.bypass")) {
            Long nextUse = rtpCooldown.get(player.getUniqueId());
            long now = System.currentTimeMillis();
            if (nextUse != null && nextUse > now) {
                send(player, "on-cooldown", "%time%", Text.time((nextUse - now) / 1000L));
                return;
            }
            rtpCooldown.put(player.getUniqueId(), now + cooldownSeconds * 1000L);
        }

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
        send(player, "teleporting");
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

    private Location findSafeLocation(World world) {
        int minRadius = config.getInt("min-radius", 100);
        int maxRadius = config.getInt("max-radius", 5000);
        int centerX = config.getInt("center-x", 0);
        int centerZ = config.getInt("center-z", 0);
        int attempts = config.getInt("max-attempts", 25);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < attempts; i++) {
            int distance = random.nextInt(minRadius, Math.max(minRadius + 1, maxRadius));
            double angle = random.nextDouble() * Math.PI * 2;
            int x = centerX + (int) (Math.cos(angle) * distance);
            int z = centerZ + (int) (Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight()) continue;
            Block ground = world.getBlockAt(x, y, z);
            Material type = ground.getType();
            if (type == Material.LAVA || type == Material.WATER || type == Material.CACTUS
                    || type == Material.MAGMA_BLOCK || type == Material.POWDER_SNOW || type.isAir()) continue;
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }
        return null;
    }
}

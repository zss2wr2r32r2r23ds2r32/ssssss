package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Portal RTP - touching a nether portal in the "factions" world opens a GUI
 * that randomly teleports you into the "world" world. Also available as /rtp.
 */
public final class PortalRtpModule extends Module implements CommandExecutor {

    private static final class RtpHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int TELEPORT_SLOT = 13;
    private static final int CANCEL_SLOT = 22;

    /** Anti-spam: portal contact only opens the GUI once every few seconds. */
    private final Map<UUID, Long> portalGuiCooldown = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();

    public PortalRtpModule(ShardedCore plugin) {
        super(plugin, "portalrtp");
    }

    @Override
    protected void onEnable() {
        registerCommand("rtp", this);
    }

    @Override
    protected void onDisable() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof RtpHolder) {
                player.closeInventory();
            }
        }
        portalGuiCooldown.clear();
        rtpCooldown.clear();
    }

    private String portalWorld() {
        return config.getString("portal-world", "factions");
    }

    private String targetWorld() {
        return config.getString("target-world", "world");
    }

    /* ----------------------------- portal hook ----------------------------- */

    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getLocation().getBlock().getType() != Material.NETHER_PORTAL) return;
        if (!player.getWorld().getName().equalsIgnoreCase(portalWorld())) return;
        if (!player.hasPermission("sharded.rtp.use")) return;

        long now = System.currentTimeMillis();
        Long last = portalGuiCooldown.get(player.getUniqueId());
        if (last != null && now - last < 3000L) return;
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof RtpHolder) return;
        portalGuiCooldown.put(player.getUniqueId(), now);
        openGui(player);
    }

    /** Stop actual nether travel from portals in the RTP world. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPortal(PlayerPortalEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                && event.getFrom().getWorld() != null
                && event.getFrom().getWorld().getName().equalsIgnoreCase(portalWorld())) {
            event.setCancelled(true);
        }
    }

    /* ----------------------------- command ----------------------------- */

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

    /* ----------------------------- gui ----------------------------- */

    private void openGui(Player player) {
        RtpHolder holder = new RtpHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, Text.c(config.getString("gui-title", "&5Random Teleport")));
        holder.inventory = inventory;

        var filler = new ItemBuilder(Material.PURPLE_STAINED_GLASS_PANE).name("&r").build();
        for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, filler);

        inventory.setItem(TELEPORT_SLOT, new ItemBuilder(Material.ENDER_PEARL)
                .name(raw("gui-teleport-name", "%world%", targetWorld()))
                .lore(raw("gui-teleport-lore-1"), raw("gui-teleport-lore-2", "%world%", targetWorld()))
                .glow(true)
                .build());
        inventory.setItem(CANCEL_SLOT, new ItemBuilder(Material.BARRIER)
                .name(raw("gui-cancel-name"))
                .build());

        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.4f, 1.6f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RtpHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (event.getSlot() == CANCEL_SLOT) {
            player.closeInventory();
            return;
        }
        if (event.getSlot() != TELEPORT_SLOT) return;

        player.closeInventory();
        teleport(player);
    }

    /* ----------------------------- teleport ----------------------------- */

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
            if (type == Material.LAVA || type == Material.WATER
                    || type == Material.CACTUS || type == Material.MAGMA_BLOCK
                    || type == Material.POWDER_SNOW || type.isAir()) {
                continue;
            }
            return new Location(world, x + 0.5, y + 1.0, z + 0.5);
        }
        return null;
    }
}

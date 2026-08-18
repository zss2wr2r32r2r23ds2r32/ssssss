package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PortalRtpModule extends Module implements CommandExecutor, TabCompleter {

    private NamespacedKey wandKey;
    private PortalTriggerStore triggers;
    private final Map<UUID, Long> portalGuiCooldown = new HashMap<>();
    private final Map<UUID, Long> rtpCooldown = new HashMap<>();
    private final Map<UUID, PendingTeleport> pending = new HashMap<>();

    private record PendingTeleport(Location start, BukkitTask task) {
    }

    public PortalRtpModule(ShardedCore plugin) {
        super(plugin, "portalrtp");
    }

    @Override
    protected void onEnable() {
        registerCommand("rtp", this);
        wandKey = new NamespacedKey(plugin, "portal_wand");
        triggers = new PortalTriggerStore(plugin, moduleFolder());

        File guiFile = new File(moduleFolder(), "gui.yml");
        if (!guiFile.exists()) plugin.saveResource("modules/portalrtp/gui.yml", false);
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
        return config.getString("portal-world", "factions");
    }

    private String targetWorld() {
        return config.getString("target-world", "world");
    }

    @EventHandler
    public void onPortalEnter(EntityPortalEnterEvent event) {
        if (event.getEntity() instanceof Player player) tryOpen(player, event.getLocation());
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
        if (triggers.isTrigger(to) || triggers.isTrigger(to.clone().subtract(0, 1, 0))) {
            tryOpen(event.getPlayer(), to);
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
        if (args.length > 0 && args[0].equalsIgnoreCase("wand")) {
            if (!player.hasPermission("sharded.rtp.admin")) {
                send(player, "no-permission");
                return true;
            }
            ItemStack wand = new ItemBuilder(Material.BLAZE_ROD)
                    .name(raw("wand-name"))
                    .lore(java.util.Arrays.asList(raw("wand-lore").split("\\|")))
                    .glow(true)
                    .edit(meta -> meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1))
                    .build();
            player.getInventory().addItem(wand);
            send(player, "wand-given");
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
    public void onWandUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        ItemStack item = event.getItem();
        if (item == null || item.getItemMeta() == null
                || !item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE)) return;
        if (!event.getPlayer().hasPermission("sharded.rtp.admin")) return;
        event.setCancelled(true);
        triggers.add(event.getClickedBlock().getLocation());
        send(event.getPlayer(), "wand-set", "%count%", String.valueOf(triggers.count()),
                "%block%", event.getClickedBlock().getType().name());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId(), false);
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
            if (config.getBoolean("countdown-actionbar", true)) {
                player.sendActionBar(Text.c(msg));
            } else {
                player.sendMessage(Text.c(msg));
            }
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

    private Location findSafeLocation(World world) {
        int minRadius = config.getInt("min-radius", 100);
        int maxRadius = config.getInt("max-radius", 50000);
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

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteHelper.ifPermission(sender, "sharded.rtp.admin", args[0], "wand");
        }
        return java.util.List.of();
    }
}

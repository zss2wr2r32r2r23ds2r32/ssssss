package com.sharded.core.modules.portalrtp;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.duel.DuelModule;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.SafeLocationFinder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PortalRtpModule extends Module implements CommandExecutor, TabCompleter {

    private PortalTriggerStore triggers;
    private final Map<UUID, Long> portalGuiCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, Long> rtpCooldown = new ConcurrentHashMap<>();
    private final Map<UUID, PendingTeleport> pending = new ConcurrentHashMap<>();
    private final Set<String> unlocked = ConcurrentHashMap.newKeySet();
    private String portalWorldName = "spawn";

    private record PendingTeleport(Location start, BukkitTask task, String destinationId) {
    }

    public PortalRtpModule(ShardedCore plugin) {
        super(plugin, "portalrtp");
    }

    @Override
    protected void onEnable() {
        registerCommand("rtp", this);
        registerCommand("unlock", this);
        triggers = new PortalTriggerStore(plugin, moduleFolder());
        portalWorldName = config.getString("portal-world", "spawn");
        unlocked.clear();
        unlocked.addAll(config.getStringList("unlocked-by-default"));
        loadUnlockState();

        File guiFile = syncJarResource("gui.yml");
        plugin.gui().loadMenu(guiFile, "portalrtp");
        plugin.gui().registerAction("rtp_confirm", p -> startCountdown(p, config.getString("default-destination", "overworld")));
        plugin.gui().registerAction("rtp_overworld", p -> startCountdown(p, "overworld"));
        plugin.gui().registerAction("rtp_nether", p -> startCountdown(p, "nether"));
        plugin.gui().registerAction("rtp_end", p -> startCountdown(p, "end"));
        plugin.gui().registerAction("rtp_duels", this::openDuels);
    }

    private void loadUnlockState() {
        File file = new File(moduleFolder(), "unlocks.yml");
        if (!file.exists()) return;
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getStringList("unlocked")) {
            if (key != null) unlocked.add(key.toLowerCase(Locale.ROOT));
        }
    }

    private void saveUnlockState() {
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("unlocked", List.copyOf(unlocked));
        try {
            yaml.save(new File(moduleFolder(), "unlocks.yml"));
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDisable() {
        for (UUID uuid : pending.keySet()) {
            cancelPending(uuid, false);
        }
        pending.clear();
        saveUnlockState();
    }

    private String portalWorld() {
        return portalWorldName;
    }

    public String targetWorldName() {
        return destinationWorld("overworld");
    }

    private String destinationWorld(String id) {
        ConfigurationSection section = config.getConfigurationSection("destinations." + id);
        if (section != null) return section.getString("world", "world");
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
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PendingTeleport pt = pending.get(uuid);
        if (pt != null) {
            Location to = event.getTo();
            if (to != null && moved(pt.start(), to)) {
                cancelPending(uuid, true);
            }
            return;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(portalWorldName)) return;
        Location to = event.getTo();
        if (to == null) return;
        if (onTriggerBlock(to)) {
            tryOpen(player, to.getBlock().getLocation());
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
        if (location == null || location.getWorld() == null || triggers == null) return false;
        if (!location.getWorld().getName().equalsIgnoreCase(portalWorldName)) return false;
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
        if (command.getName().equalsIgnoreCase("unlock")) {
            return handleUnlock(sender, args);
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.rtp.use")) {
            send(player, "no-permission");
            return true;
        }
        if (!canUseRtp(player)) {
            send(player, "wrong-world", "%world%", portalWorld());
            return true;
        }
        openGui(player);
        return true;
    }

    private boolean handleUnlock(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sharded.rtp.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "unlock-usage");
            return true;
        }
        String dest = args[0].toLowerCase(Locale.ROOT);
        if (!config.isConfigurationSection("destinations." + dest)) {
            send(sender, "unknown-destination", "%destination%", dest);
            return true;
        }
        unlocked.add(dest);
        saveUnlockState();
        send(sender, "unlocked", "%destination%", dest);
        return true;
    }

    private boolean canUseRtp(Player player) {
        if (player.hasPermission("sharded.rtp.bypass")) return true;
        if (!config.getBoolean("require-portal-world", true)) return true;
        return player.getWorld().getName().equalsIgnoreCase(portalWorld());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId(), false);
        portalGuiCooldown.remove(event.getPlayer().getUniqueId());
    }

    private void openGui(Player player) {
        if (plugin.gui().menu("portalrtp") == null) {
            plugin.gui().loadMenu(syncJarResource("gui.yml"), "portalrtp");
        }
        Map<String, String> ph = Map.of(
                "target_world", destinationWorld("overworld"),
                "radius", config.getString("radius-label", "50K x 50K"),
                "border", config.getString("border-label", "100K x 100K"),
                "players_online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        plugin.gui().open(player, "portalrtp", ph);
    }

    private void openDuels(Player player) {
        player.closeInventory();
        if (config.getBoolean("duels.use-duel-command", true)) {
            player.performCommand("duel queue");
            return;
        }
        List<String> commands = config.getStringList("duels.commands");
        for (String line : commands) {
            String cmd = line.startsWith("/") ? line.substring(1) : line;
            player.performCommand(cmd);
        }
    }

    private void startCountdown(Player player, String destinationId) {
        if (!canUseRtp(player)) {
            send(player, "wrong-world", "%world%", portalWorld());
            return;
        }
        ConfigurationSection dest = config.getConfigurationSection("destinations." + destinationId);
        if (dest == null) {
            send(player, "unknown-destination", "%destination%", destinationId);
            return;
        }
        if (dest.getBoolean("locked", false) && !unlocked.contains(destinationId.toLowerCase(Locale.ROOT))
                && !player.hasPermission("sharded.rtp.bypass")) {
            send(player, "destination-locked", "%destination%", dest.getString("display-name", destinationId));
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
        String actionBarTemplate = dest.getString("countdown-actionbar",
                config.getString("countdown-actionbar",
                        "&#9FFF00&lRTP &8▷ &fTeleporting in &#9FFF00&n%seconds%&r&#9FFF00s"));
        String countdownSound = dest.getString("countdown-sound", config.getString("countdown-sound", "BLOCK_NOTE_BLOCK_PLING"));
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelPending(player.getUniqueId(), false);
                return;
            }
            if (remaining[0] <= 0) {
                cancelPending(player.getUniqueId(), false);
                finishTeleport(player, destinationId, cooldownSeconds);
                return;
            }
            player.sendActionBar(Text.c(actionBarTemplate.replace("%seconds%", String.valueOf(remaining[0]))));
            playSound(player, countdownSound);
            remaining[0]--;
        }, 0L, 20L);
        pending.put(player.getUniqueId(), new PendingTeleport(start, task, destinationId));
    }

    private void cancelPending(UUID uuid, boolean notify) {
        PendingTeleport pt = pending.remove(uuid);
        if (pt != null) pt.task().cancel();
        if (notify) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) notifyTeleportCancelled(player);
        }
    }

    private void notifyTeleportCancelled(Player player) {
        String bar = config.getString("teleport-cancelled-actionbar",
                "&#9FFF00&lRTP &8▷ &fYou moved &8— &7teleport cancelled.");
        player.sendActionBar(Text.cPlain(bar));
        playSound(player, config.getString("cancel-sound", "BLOCK_NOTE_BLOCK_BASS"));
    }

    private void playSound(Player player, String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(raw.toUpperCase(Locale.ROOT)), 0.8f, 0.8f);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void finishTeleport(Player player, String destinationId, long cooldownSeconds) {
        ConfigurationSection dest = config.getConfigurationSection("destinations." + destinationId);
        String worldName = dest == null ? config.getString("target-world", "world") : dest.getString("world", "world");
        World world = resolveWorld(worldName);
        if (world == null) {
            send(player, "world-not-found", "%world%", worldName);
            return;
        }
        Location location = findSafeLocation(world, dest);
        if (location == null) {
            send(player, "no-safe-location");
            return;
        }
        if (!player.hasPermission("sharded.rtp.bypass")) {
            rtpCooldown.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds * 1000L);
        }
        Location finalLocation = location;
        World finalWorld = world;
        String successKey = dest == null ? "teleported" : dest.getString("success-message-key", "teleported");
        player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(success -> {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (success) {
                    playSound(player, dest == null ? "ENTITY_ENDERMAN_TELEPORT" : dest.getString("success-sound", "ENTITY_ENDERMAN_TELEPORT"));
                    send(player, successKey,
                            "%x%", String.valueOf(finalLocation.getBlockX()),
                            "%y%", String.valueOf(finalLocation.getBlockY()),
                            "%z%", String.valueOf(finalLocation.getBlockZ()),
                            "%world%", finalWorld.getName());
                    String actionBar = dest == null ? null : dest.getString("success-actionbar");
                    if (actionBar != null && !actionBar.isBlank()) {
                        player.sendActionBar(Text.c(Text.apply(actionBar, "%world%", finalWorld.getName())));
                    }
                } else {
                    send(player, "teleport-failed");
                }
            });
        });
    }

    private World resolveWorld(String name) {
        if (name == null || name.isBlank()) return null;
        World world = Bukkit.getWorld(name);
        if (world != null) return world;
        for (World loaded : Bukkit.getWorlds()) {
            if (loaded.getName().equalsIgnoreCase(name)) return loaded;
        }
        return null;
    }

    public Location findSafeLocation(World world) {
        return findSafeLocation(world, config.getConfigurationSection("destinations.overworld"));
    }

    private Location findSafeLocation(World world, ConfigurationSection dest) {
        ConfigurationSection settings = dest != null ? dest.getConfigurationSection("rtp") : null;
        if (settings != null) {
            Location found = SafeLocationFinder.find(world, settings);
            if (found != null) return found;
        }
        Location found = SafeLocationFinder.find(world, config);
        if (found != null) return found;
        int attempts = config.getInt("max-attempts", 25) * 2;
        return SafeLocationFinder.find(world,
                config.getInt("center-x", 0),
                config.getInt("center-z", 0),
                config.getInt("min-radius", 100),
                config.getInt("max-radius", 500),
                attempts);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("unlock")) {
            if (!sender.hasPermission("sharded.rtp.admin")) return List.of();
            if (args.length == 1) {
                ConfigurationSection destinations = config.getConfigurationSection("destinations");
                if (destinations == null) return List.of();
                return TabCompleteHelper.filter(args[0], destinations.getKeys(false));
            }
        }
        return List.of();
    }
}

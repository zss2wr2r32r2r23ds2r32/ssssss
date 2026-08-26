package com.sharded.core.modules.coreprotect;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Region protection, side arenas, snapshots, and auto-reset. Replaces protect + arena modules. */
public final class CoreProtectModule extends Module implements CommandExecutor, TabCompleter {

    private static final List<String> SIDE_KEYS = List.of("side1", "side2", "side3", "side4");
    private static final List<String> REGION_IDS = List.of(
            "spawn", "combat", "pvp", "side1", "side2", "side3", "side4");

    private static final Set<Material> SPAWN_BLOCKED_USE = Set.of(
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.BEACON, Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR,
            Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR,
            Material.DARK_OAK_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR,
            Material.BAMBOO_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR,
            Material.IRON_TRAPDOOR);

    private final RegionSetup setup = new RegionSetup();
    private final Map<String, CuboidRegion> regions = new HashMap<>();
    private Set<String> sideWorlds = Set.of();
    private Set<String> hornBlockWorlds = Set.of("spawn");
    private ArenaService arenaService;
    private PlayerPlacedTracker placedTracker;
    private BukkitTask autoResetTask;

    public CoreProtectModule(ShardedCore plugin) {
        super(plugin, "coreprotect");
    }

    @Override
    protected void onEnable() {
        migrateLegacyData();
        reloadRegions();
        arenaService = new ArenaService(plugin, moduleFolder());
        placedTracker = new PlayerPlacedTracker(moduleFolder());
        registerCommand("coreprotect", this);
        registerCommand("arenas", this);
        registerCommand("protect", this);
        startAutoReset();
    }

    private void migrateLegacyData() {
        File target = moduleFolder();
        File legacyProtect = new File(plugin.getDataFolder(), "modules/protect/config.yml");
        File legacyArena = new File(plugin.getDataFolder(), "modules/arena");
        if (!target.exists()) target.mkdirs();

        boolean needsRegionImport = config.getConfigurationSection("regions.spawn") == null
                || CuboidRegion.fromSection(config.getConfigurationSection("regions.spawn")) == null;
        if (needsRegionImport && legacyProtect.isFile()) {
            var legacy = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(legacyProtect);
            for (String key : REGION_IDS) {
                if (config.getConfigurationSection("regions." + key) != null
                        && CuboidRegion.fromSection(config.getConfigurationSection("regions." + key)) != null) {
                    continue;
                }
                var section = legacy.getConfigurationSection("regions." + key);
                if (section != null) {
                    config.set("regions." + key, section.getValues(false));
                }
            }
            if (legacy.contains("side-max-build-y")) {
                config.set("side-max-build-y", legacy.getInt("side-max-build-y"));
            }
            if (legacy.contains("horn-block-worlds")) {
                config.set("horn-block-worlds", legacy.getStringList("horn-block-worlds"));
            }
            saveConfig();
            plugin.getLogger().info("[coreprotect] Imported regions from legacy modules/protect/config.yml");
        }

        File legacySnaps = new File(legacyArena, "snapshots");
        File targetSnaps = new File(target, "snapshots");
        if (legacySnaps.isDirectory() && legacySnaps.list() != null && legacySnaps.list().length > 0) {
            if (!targetSnaps.exists()) targetSnaps.mkdirs();
            for (File snap : legacySnaps.listFiles((dir, name) -> name.endsWith(".snap"))) {
                File dest = new File(targetSnaps, snap.getName());
                if (!dest.exists()) {
                    try {
                        java.nio.file.Files.copy(snap.toPath(), dest.toPath());
                    } catch (Exception e) {
                        plugin.getLogger().warning("[coreprotect] Could not copy snapshot " + snap.getName());
                    }
                }
            }
        }

        File legacyArenaConfig = new File(legacyArena, "config.yml");
        if (legacyArenaConfig.isFile() && !config.contains("auto-reset.enabled")) {
            var legacyCfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(legacyArenaConfig);
            if (legacyCfg.contains("auto-reset")) {
                config.set("auto-reset", legacyCfg.getConfigurationSection("auto-reset").getValues(false));
                saveConfig();
            }
        }
    }

    @Override
    protected void onDisable() {
        if (autoResetTask != null) autoResetTask.cancel();
        if (placedTracker != null) placedTracker.save();
    }

    private void reloadRegions() {
        regions.clear();
        Set<String> worlds = new HashSet<>();
        for (String key : REGION_IDS) {
            CuboidRegion r = CuboidRegion.fromSection(config.getConfigurationSection("regions." + key));
            if (r != null) {
                regions.put(key, r);
                if (SIDE_KEYS.contains(key)) {
                    worlds.add(r.world().toLowerCase(Locale.ROOT));
                }
            }
        }
        sideWorlds = Set.copyOf(worlds);
        List<String> hornWorlds = config.getStringList("horn-block-worlds");
        hornBlockWorlds = hornWorlds.isEmpty() ? Set.of("spawn") : new HashSet<>(hornWorlds);
    }

    public CuboidRegion region(String id) {
        return regions.get(id);
    }

    public boolean inSpawn(Location loc) {
        CuboidRegion spawn = regions.get("spawn");
        return spawn != null && spawn.contains(loc);
    }

    public boolean inCombat(Location loc) {
        CuboidRegion combat = regions.get("combat");
        return combat != null && combat.contains(loc);
    }

    public boolean inPvp(Location loc) {
        CuboidRegion pvp = regions.get("pvp");
        return pvp != null && pvp.contains(loc);
    }

    public String sideAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        for (String key : SIDE_KEYS) {
            CuboidRegion side = regions.get(key);
            if (side == null) continue;
            if (x >= side.minX() && x <= side.maxX()
                    && y >= side.minY() && y <= side.maxY()
                    && z >= side.minZ() && z <= side.maxZ()) {
                return key;
            }
        }
        return null;
    }

    public boolean inSide(Location loc) {
        return sideAt(loc) != null;
    }

    public boolean bypass(Player player) {
        return player.hasPermission("sharded.coreprotect.bypass")
                || player.hasPermission("sharded.protect.bypass");
    }

    private boolean hornBlocked(Player player) {
        if (bypass(player)) return false;
        String world = player.getWorld().getName();
        if (inSpawn(player.getLocation())) return true;
        for (String blocked : hornBlockWorlds) {
            if (blocked.equalsIgnoreCase(world)) return true;
        }
        return false;
    }

    private boolean restrictSpawnPvp(Player player, Location loc) {
        if (bypass(player)) return false;
        if (inSide(loc)) return false;
        return inSpawn(loc) || inPvp(loc);
    }

    private boolean restrictSpawnOnly(Player player, Location loc) {
        if (bypass(player)) return false;
        if (inSide(loc)) return false;
        return inSpawn(loc);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("arenas")) {
            return handleArenas(sender, args);
        }
        if (cmd.equals("protect")) {
            if (sender instanceof Player player) {
                send(player, "protect-deprecated");
            }
            return handleCoreProtect(sender, args);
        }
        return handleCoreProtect(sender, args);
    }

    private boolean handleCoreProtect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.coreprotect.admin")
                && !player.hasPermission("sharded.protect.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(player, "usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("pos1")) {
            setup.setPos1(player, player.getLocation());
            send(player, "pos1-set");
            return true;
        }
        if (sub.equals("pos2")) {
            setup.setPos2(player, player.getLocation());
            send(player, "pos2-set");
            return true;
        }
        if (sub.equals("setregion") && args.length >= 2) {
            String id = args[1].toLowerCase(Locale.ROOT);
            if (!REGION_IDS.contains(id)) {
                send(player, "unknown-region");
                return true;
            }
            CuboidRegion built = setup.build(player);
            if (built == null) {
                send(player, "need-positions");
                return true;
            }
            regions.put(id, built);
            built.write(config.getConfigurationSection("regions." + id) != null
                    ? config.getConfigurationSection("regions." + id)
                    : config.createSection("regions." + id));
            if (SIDE_KEYS.contains(id)) {
                reloadRegions();
            }
            saveConfig();
            send(player, "region-set", "%region%", id);
            return true;
        }
        send(player, "usage");
        return true;
    }

    private boolean handleArenas(CommandSender sender, String[] args) {
        if (args.length == 0) {
            send(sender, "arenas-usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("snapshot") && args.length >= 2) {
            if (!sender.hasPermission("sharded.coreprotect.admin")
                    && !sender.hasPermission("sharded.arena.admin")) {
                send(sender, "no-permission");
                return true;
            }
            List<String> ids = resolveArenaIds(args[1]);
            int total = 0;
            for (String id : ids) {
                CuboidRegion region = regions.get(id);
                if (region == null) {
                    send(sender, "no-region", "%arena%", id);
                    continue;
                }
                total += arenaService.snapshot(id, region);
            }
            send(sender, "snapshot-done", "%count%", String.valueOf(total));
            return true;
        }
        if (sub.equals("reset") && args.length >= 2) {
            if (!sender.hasPermission("sharded.coreprotect.admin")
                    && !sender.hasPermission("sharded.arena.admin")) {
                send(sender, "no-permission");
                return true;
            }
            boolean fast = args.length >= 3 && args[2].equalsIgnoreCase("fast");
            List<String> ids = resolveArenaIds(args[1]);
            for (String id : ids) {
                if (!arenaService.hasSnapshot(id)) {
                    send(sender, "no-snapshot", "%arena%", id);
                    continue;
                }
                arenaService.reset(id, fast, () -> {
                    placedTracker.clearSide(id);
                    placedTracker.save();
                    send(sender, "reset-done", "%arena%", id);
                });
            }
            return true;
        }
        send(sender, "arenas-usage");
        return true;
    }

    private void startAutoReset() {
        if (!config.getBoolean("auto-reset.enabled", true)) return;
        long minutes = config.getLong("auto-reset.interval-minutes", 15L);
        long ticks = Math.max(1200L, minutes * 60L * 20L);
        autoResetTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::runAutoReset, ticks, ticks);
    }

    private void runAutoReset() {
        if (!config.getBoolean("auto-reset.enabled", true)) return;
        List<String> commands = config.getStringList("auto-reset.commands");
        if (commands.isEmpty()) {
            for (String side : SIDE_KEYS) {
                if (arenaService.hasSnapshot(side)) {
                    arenaService.reset(side, true, () -> {
                        placedTracker.clearSide(side);
                        placedTracker.save();
                    });
                }
            }
            return;
        }
        for (String line : commands) {
            if (line == null || line.isBlank()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        }
    }

    private List<String> resolveArenaIds(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("side1-side4") || lower.equals("sides") || lower.equals("all")) {
            return SIDE_KEYS;
        }
        return List.of(lower);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        if (restrictSpawnPvp(player, loc)) {
            event.setCancelled(true);
            send(player, "no-break");
            return;
        }
        if (inSide(loc)) {
            if (bypass(player)) return;
            if (!placedTracker.isPlaced(loc)) {
                event.setCancelled(true);
                send(player, "no-break");
            } else {
                placedTracker.unmark(loc);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        String side = sideAt(loc);
        if (side != null) {
            if (bypass(player)) {
                placedTracker.mark(loc);
                return;
            }
            int maxY = config.getInt("side-max-build-y", 111);
            if (loc.getBlockY() > maxY) {
                event.setCancelled(true);
                send(player, "side-build-limit", "%y%", String.valueOf(maxY));
                return;
            }
            placedTracker.mark(loc);
            return;
        }
        if (!restrictSpawnOnly(player, loc)) return;
        event.setCancelled(true);
        send(player, "no-place");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (inSide(victim.getLocation())) return;
        if (inSpawn(victim.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!config.getBoolean("block-natural-mobs-in-spawn", true)) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.JOCKEY) return;
        if (!inSpawn(event.getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (inSide(player.getLocation())) return;
        if (inSpawn(player.getLocation())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHornUse(PlayerInteractEvent event) {
        if (!isHornInteraction(event)) return;
        if (!hornBlocked(event.getPlayer())) return;
        denyHorn(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHornUseGuard(PlayerInteractEvent event) {
        if (!isHornInteraction(event)) return;
        if (!hornBlocked(event.getPlayer())) return;
        denyHorn(event, event.getPlayer());
    }

    private boolean isHornInteraction(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.GOAT_HORN) return false;
        return event.getAction().isRightClick();
    }

    private void denyHorn(PlayerInteractEvent event, Player player) {
        event.setCancelled(true);
        event.setUseItemInHand(Result.DENY);
        event.setUseInteractedBlock(Result.DENY);
        player.setCooldown(Material.GOAT_HORN, 20);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHornConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.GOAT_HORN) return;
        if (hornBlocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSideInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (sideWorlds.isEmpty()) return;
        if (!sideWorlds.contains(block.getWorld().getName().toLowerCase(Locale.ROOT))) return;
        Player player = event.getPlayer();
        if (bypass(player)) return;
        Location loc = block.getLocation();
        if (!inSide(loc)) return;

        if (event.getAction().isRightClick() && event.getItem() != null && allowsSidePlacement(event.getItem().getType())) {
            return;
        }
        if (!SideBlockedMaterials.isBlocked(block.getType())) return;

        event.setCancelled(true);
        event.setUseInteractedBlock(Result.DENY);
        send(player, "no-use");
    }

    private static boolean allowsSidePlacement(Material item) {
        if (item.isBlock()) return true;
        String name = item.name();
        return name.endsWith("_SEEDS") || item == Material.BONE_MEAL || item == Material.NETHER_WART;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        if (bypass(player)) return;
        Location loc = block.getLocation();
        if (!sideWorlds.isEmpty() && sideWorlds.contains(loc.getWorld().getName().toLowerCase(Locale.ROOT))) {
            if (inSide(loc)) return;
        }
        if (!inSpawn(loc) && !inPvp(loc)) return;
        Material type = block.getType();
        if (SPAWN_BLOCKED_USE.contains(type) || type.name().endsWith("_TRAPDOOR")) {
            event.setCancelled(true);
            send(player, "no-use");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPearl(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Location to = event.getTo();
        if (to == null || bypass(player) || inSide(to)) return;
        if (inSpawn(to)) {
            event.setCancelled(true);
            send(player, "no-pearl-spawn");
        }
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[coreprotect] Could not save config: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("arenas")) {
            if (!sender.hasPermission("sharded.coreprotect.admin")
                    && !sender.hasPermission("sharded.arena.admin")) return List.of();
            if (args.length == 1) return TabCompleteHelper.filter(args[0], "snapshot", "reset");
            if (args.length == 2) {
                List<String> ids = new ArrayList<>(SIDE_KEYS);
                ids.add("side1-side4");
                return TabCompleteHelper.filter(args[1], ids);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
                return TabCompleteHelper.filter(args[2], "fast");
            }
            return List.of();
        }
        if (!sender.hasPermission("sharded.coreprotect.admin")
                && !sender.hasPermission("sharded.protect.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion");
        if (args.length == 2 && args[0].equalsIgnoreCase("setregion")) {
            return TabCompleteHelper.filter(args[1], REGION_IDS);
        }
        return List.of();
    }
}

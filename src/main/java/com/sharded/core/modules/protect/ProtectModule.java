package com.sharded.core.modules.protect;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Spawn / PVP / side region protection. Side regions allow PvP, building, pearls, and damage. */
public final class ProtectModule extends Module implements CommandExecutor, TabCompleter {

    private static final List<String> SIDE_KEYS = List.of("side1", "side2", "side3", "side4");
    private static final List<Material> BLOCKED_USE = List.of(
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.BEACON, Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR,
            Material.BIRCH_TRAPDOOR, Material.JUNGLE_TRAPDOOR, Material.ACACIA_TRAPDOOR,
            Material.DARK_OAK_TRAPDOOR, Material.MANGROVE_TRAPDOOR, Material.CHERRY_TRAPDOOR,
            Material.BAMBOO_TRAPDOOR, Material.CRIMSON_TRAPDOOR, Material.WARPED_TRAPDOOR,
            Material.IRON_TRAPDOOR);

    private final RegionSetup setup = new RegionSetup();
    private final Map<String, CuboidRegion> regions = new HashMap<>();

    public ProtectModule(ShardedCore plugin) {
        super(plugin, "protect");
    }

    @Override
    protected void onEnable() {
        reloadRegions();
        registerCommand("protect", this);
    }

    private void reloadRegions() {
        regions.clear();
        for (String key : List.of("spawn", "pvp", "side1", "side2", "side3", "side4")) {
            CuboidRegion r = CuboidRegion.fromSection(config.getConfigurationSection("regions." + key));
            if (r != null) regions.put(key, r);
        }
    }

    public CuboidRegion region(String id) {
        return regions.get(id);
    }

    public boolean inSpawn(Location loc) {
        CuboidRegion spawn = regions.get("spawn");
        return spawn != null && spawn.contains(loc);
    }

    public boolean inPvp(Location loc) {
        CuboidRegion pvp = regions.get("pvp");
        return pvp != null && pvp.contains(loc);
    }

    public boolean inSide(Location loc) {
        for (String key : SIDE_KEYS) {
            CuboidRegion side = regions.get(key);
            if (side != null && side.contains(loc)) return true;
        }
        return false;
    }

    public boolean bypass(Player player) {
        return player.hasPermission("sharded.protect.bypass");
    }

    /** Side regions override spawn/pvp restrictions. */
    private boolean restrict(Player player, Location loc) {
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
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.protect.admin")) {
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
            if (!List.of("spawn", "pvp", "side1", "side2", "side3", "side4").contains(id)) {
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
            saveConfig();
            send(player, "region-set", "%region%", id);
            return true;
        }
        send(player, "usage");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();
        if (!restrict(player, loc)) return;
        event.setCancelled(true);
        send(player, "no-break");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (inSide(victim.getLocation())) return;
        if (inSpawn(victim.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSideBuildLimit(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (bypass(player)) return;
        Location loc = event.getBlock().getLocation();
        if (!inSide(loc)) return;
        int maxY = config.getInt("side-max-build-y", 111);
        if (loc.getBlockY() > maxY) {
            event.setCancelled(true);
            send(player, "side-build-limit", "%y%", String.valueOf(maxY));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!restrictSpawnOnly(player, event.getBlock().getLocation())) return;
        event.setCancelled(true);
        send(player, "no-place");
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
        if (inSpawn(player.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        Location loc = block.getLocation();
        if (inSide(loc) || bypass(player)) return;
        if (!inSpawn(loc) && !inPvp(loc)) return;
        Material type = block.getType();
        if (BLOCKED_USE.contains(type) || type.name().endsWith("_TRAPDOOR")) {
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
            plugin.getLogger().warning("[protect] Could not save config: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.protect.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion");
        if (args.length == 2 && args[0].equalsIgnoreCase("setregion")) {
            return TabCompleteHelper.filter(args[1], "spawn", "pvp", "side1", "side2", "side3", "side4");
        }
        return List.of();
    }
}

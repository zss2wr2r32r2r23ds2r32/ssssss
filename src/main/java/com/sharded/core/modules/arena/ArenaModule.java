package com.sharded.core.modules.arena;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Resets PVP/spawn arenas from stored snapshots — batched for TPS. */
public final class ArenaModule extends Module implements CommandExecutor, TabCompleter {

    private final Map<String, List<BlockState>> snapshots = new HashMap<>();
    private final Map<String, CuboidRegion> regions = new HashMap<>();
    private int autoTask = -1;

    private record BlockState(int x, int y, int z, Material type, String blockData) {
    }

    public ArenaModule(ShardedCore plugin) {
        super(plugin, "arena");
    }

    @Override
    protected void onEnable() {
        loadSnapshots();
        registerCommand("arena", this);
        long interval = config.getLong("auto-reset-minutes", 15) * 60L * 20L;
        autoTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::resetAll, interval, interval);
    }

    @Override
    protected void onDisable() {
        if (autoTask >= 0) Bukkit.getScheduler().cancelTask(autoTask);
    }

    private void loadSnapshots() {
        snapshots.clear();
        regions.clear();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            CuboidRegion region = CuboidRegion.fromSection(section.getConfigurationSection(id + ".region"));
            if (region != null) regions.put(id, region);
            List<BlockState> blocks = new ArrayList<>();
            for (String line : section.getStringList(id + ".blocks")) {
                String[] p = line.split(",");
                if (p.length < 4) continue;
                blocks.add(new BlockState(
                        Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Material.valueOf(p[3]), p.length > 4 ? p[4] : ""));
            }
            if (!blocks.isEmpty()) snapshots.put(id, blocks);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.arena.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reset") && args.length >= 2) {
            int speed = args.length >= 3 ? parseInt(args[2], 500) : config.getInt("reset-blocks-per-tick", 500);
            resetRegion(args[1].toLowerCase(Locale.ROOT), speed);
            send(sender, "reset-started", "%region%", args[1]);
            return true;
        }
        if (sub.equals("remove") && args.length >= 2) {
            String id = args[1].toLowerCase(Locale.ROOT);
            snapshots.remove(id);
            regions.remove(id);
            config.set("arenas." + id, null);
            saveConfig();
            send(sender, "removed", "%region%", id);
            return true;
        }
        if (sub.equals("snapshot") && args.length >= 2) {
            snapshotRegion(args[1].toLowerCase(Locale.ROOT));
            send(sender, "snapshot-saved", "%region%", args[1]);
            return true;
        }
        send(sender, "usage");
        return true;
    }

    public void linkRegion(String id, CuboidRegion region) {
        regions.put(id, region);
        snapshotRegion(id);
    }

    private void snapshotRegion(String id) {
        CuboidRegion region = regions.get(id);
        if (region == null) return;
        World world = region.bukkitWorld();
        if (world == null) return;
        List<BlockState> blocks = new ArrayList<>(region.volume());
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new BlockState(x, y, z, block.getType(), block.getBlockData().getAsString()));
                }
            }
        }
        snapshots.put(id, blocks);
        config.createSection("arenas." + id + ".region");
        region.write(config.getConfigurationSection("arenas." + id + ".region"));
        List<String> encoded = new ArrayList<>();
        for (BlockState state : blocks) {
            encoded.add(state.x + "," + state.y + "," + state.z + "," + state.type.name() + "," + state.blockData);
        }
        config.set("arenas." + id + ".blocks", encoded);
        saveConfig();
    }

    private void resetAll() {
        for (String id : snapshots.keySet()) {
            resetRegion(id, config.getInt("reset-blocks-per-tick", 500));
        }
    }

    private void resetRegion(String id, int perTick) {
        List<BlockState> blocks = snapshots.get(id);
        CuboidRegion region = regions.get(id);
        if (blocks == null || region == null) return;
        World world = region.bukkitWorld();
        if (world == null) return;
        Iterator<BlockState> it = new ArrayList<>(blocks).iterator();
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int n = 0;
            while (it.hasNext() && n < perTick) {
                BlockState state = it.next();
                Block block = world.getBlockAt(state.x, state.y, state.z);
                block.setType(state.type, false);
                if (!state.blockData.isBlank()) {
                    try {
                        block.setBlockData(Bukkit.createBlockData(state.blockData));
                    } catch (Exception ignored) {
                    }
                }
                n++;
            }
            if (!it.hasNext()) task.cancel();
        }, 0L, 1L);
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[arena] Could not save config: " + e.getMessage());
        }
    }

    private int parseInt(String raw, int def) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.arena.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "reset", "remove", "snapshot");
        if (args.length == 2) return TabCompleteHelper.filter(args[1], snapshots.keySet());
        return List.of();
    }
}

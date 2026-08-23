package com.sharded.core.modules.arena;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/** Persists arena block snapshots outside config.yml to keep reloads fast. */
final class ArenaSnapshotStorage {

    private ArenaSnapshotStorage() {
    }

    static List<ArenaModule.BlockState> load(File moduleFolder, String id, ConfigurationSection arenaSection,
                                             Logger log) {
        File snap = snapFile(moduleFolder, id);
        if (snap.isFile()) {
            try {
                return readSnap(snap);
            } catch (IOException e) {
                log.warning("[arena] Could not read snapshot " + id + ": " + e.getMessage());
            }
        }
        if (arenaSection == null) return List.of();
        List<String> legacy = arenaSection.getStringList("blocks");
        if (legacy.isEmpty()) return List.of();
        List<ArenaModule.BlockState> blocks = decodeLegacy(legacy);
        if (!blocks.isEmpty()) {
            try {
                writeSnap(snap, blocks);
                log.info("[arena] Migrated snapshot '" + id + "' from config.yml to " + snap.getName());
            } catch (IOException e) {
                log.warning("[arena] Could not migrate snapshot " + id + ": " + e.getMessage());
            }
        }
        return blocks;
    }

    static void save(File moduleFolder, String id, List<ArenaModule.BlockState> blocks) throws IOException {
        writeSnap(snapFile(moduleFolder, id), blocks);
    }

    static void delete(File moduleFolder, String id) {
        File snap = snapFile(moduleFolder, id);
        if (snap.isFile() && !snap.delete()) {
            snap.deleteOnExit();
        }
    }

    static void stripBlocksFromConfig(ConfigurationSection arenasSection, Logger log) {
        if (arenasSection == null) return;
        for (String id : arenasSection.getKeys(false)) {
            if (arenasSection.isList(id + ".blocks") || arenasSection.isString(id + ".blocks")) {
                arenasSection.set(id + ".blocks", null);
                log.info("[arena] Removed legacy blocks list for '" + id + "' from config.yml");
            }
        }
    }

    static List<ArenaModule.BlockState> capture(World world, com.sharded.core.util.CuboidRegion region) {
        List<ArenaModule.BlockState> blocks = new ArrayList<>(region.volume());
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new ArenaModule.BlockState(x, y, z, block.getType(), block.getBlockData().getAsString()));
                }
            }
        }
        return blocks;
    }

    private static File snapFile(File moduleFolder, String id) {
        File dir = new File(moduleFolder, "snapshots");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, id + ".snap");
    }

    private static List<ArenaModule.BlockState> decodeLegacy(List<String> lines) {
        List<ArenaModule.BlockState> blocks = new ArrayList<>(lines.size());
        for (String line : lines) {
            String[] p = line.split(",", 5);
            if (p.length < 4) continue;
            try {
                blocks.add(new ArenaModule.BlockState(
                        Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Material.valueOf(p[3]), p.length > 4 ? p[4] : ""));
            } catch (Exception ignored) {
            }
        }
        return blocks;
    }

    private static List<ArenaModule.BlockState> readSnap(File file) throws IOException {
        List<ArenaModule.BlockState> blocks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = line.split("\t", 5);
                if (p.length < 4) continue;
                blocks.add(new ArenaModule.BlockState(
                        Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                        Material.valueOf(p[3]), p.length > 4 ? p[4] : ""));
            }
        }
        return blocks;
    }

    private static void writeSnap(File file, List<ArenaModule.BlockState> blocks) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (ArenaModule.BlockState state : blocks) {
                writer.write(state.x() + "\t" + state.y() + "\t" + state.z() + "\t"
                        + state.type().name() + "\t" + state.blockData());
                writer.newLine();
            }
        }
    }
}

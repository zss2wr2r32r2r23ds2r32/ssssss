package com.sharded.core.modules.coreprotect;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.CuboidRegion;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Snapshots and restores arena cuboids from {@code .snap} files. */
final class ArenaService {

    private record BlockEntry(int x, int y, int z, Material material, String blockData) {
    }

    private final ShardedCore plugin;
    private final File folder;

    ArenaService(ShardedCore plugin, File moduleFolder) {
        this.plugin = plugin;
        this.folder = new File(moduleFolder, "snapshots");
        if (!folder.exists()) folder.mkdirs();
    }

    File snapFile(String arenaId) {
        return new File(folder, arenaId.toLowerCase() + ".snap");
    }

    boolean hasSnapshot(String arenaId) {
        return snapFile(arenaId).isFile();
    }

    int snapshot(String arenaId, CuboidRegion region) {
        if (region == null) return 0;
        World world = region.bukkitWorld();
        if (world == null) return 0;
        List<BlockEntry> blocks = new ArrayList<>();
        for (int x = region.minX(); x <= region.maxX(); x++) {
            for (int y = region.minY(); y <= region.maxY(); y++) {
                for (int z = region.minZ(); z <= region.maxZ(); z++) {
                    Block block = world.getBlockAt(x, y, z);
                    blocks.add(new BlockEntry(x, y, z, block.getType(), block.getBlockData().getAsString()));
                }
            }
        }
        File file = snapFile(arenaId);
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file)))) {
            out.writeUTF(world.getName());
            out.writeInt(blocks.size());
            for (BlockEntry entry : blocks) {
                out.writeInt(entry.x);
                out.writeInt(entry.y);
                out.writeInt(entry.z);
                out.writeUTF(entry.material.name());
                out.writeUTF(entry.blockData);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[coreprotect] Could not save snapshot for " + arenaId + ": " + e.getMessage());
            return 0;
        }
        return blocks.size();
    }

    void reset(String arenaId, boolean fast, Runnable onComplete) {
        File file = snapFile(arenaId);
        if (!file.isFile()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        SnapshotData data = readSnapshot(file);
        if (data == null || data.blocks.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        World world = plugin.getServer().getWorld(data.worldName);
        if (world == null) {
            if (onComplete != null) onComplete.run();
            return;
        }
        int perTick = fast ? 8000 : 2000;
        List<BlockEntry> blocks = data.blocks;
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, new Runnable() {
            int index = 0;

            @Override
            public void run() {
                int end = Math.min(index + perTick, blocks.size());
                for (int i = index; i < end; i++) {
                    BlockEntry entry = blocks.get(i);
                    Block block = world.getBlockAt(entry.x, entry.y, entry.z);
                    block.setType(entry.material, false);
                    try {
                        block.setBlockData(plugin.getServer().createBlockData(entry.blockData), false);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                index = end;
                if (index >= blocks.size()) {
                    if (holder[0] != null) holder[0].cancel();
                    if (onComplete != null) onComplete.run();
                }
            }
        }, 0L, 1L);
    }

    void resetAll(List<String> arenaIds, boolean fast, Consumer<String> onEachDone) {
        resetNext(arenaIds, 0, fast, onEachDone);
    }

    private void resetNext(List<String> arenaIds, int index, boolean fast, Consumer<String> onEachDone) {
        if (index >= arenaIds.size()) return;
        String id = arenaIds.get(index);
        reset(id, fast, () -> {
            if (onEachDone != null) onEachDone.accept(id);
            resetNext(arenaIds, index + 1, fast, onEachDone);
        });
    }

    private record SnapshotData(String worldName, List<BlockEntry> blocks) {
    }

    private SnapshotData readSnapshot(File file) {
        List<BlockEntry> blocks = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            String worldName = in.readUTF();
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int x = in.readInt();
                int y = in.readInt();
                int z = in.readInt();
                Material mat = Material.matchMaterial(in.readUTF());
                String data = in.readUTF();
                if (mat == null) mat = Material.AIR;
                blocks.add(new BlockEntry(x, y, z, mat, data));
            }
            return new SnapshotData(worldName, blocks);
        } catch (IOException e) {
            plugin.getLogger().warning("[coreprotect] Could not read snapshot " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
}

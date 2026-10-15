package com.sharded.core.modules.arena;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.CuboidRegion;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

final class ArenaService {
   private final ShardedCore plugin;
   private final File folder;

   ArenaService(ShardedCore plugin, File moduleFolder) {
      this.plugin = plugin;
      this.folder = new File(moduleFolder, "snapshots");
      if (!this.folder.exists()) {
         this.folder.mkdirs();
      }
   }

   File snapFile(String arenaId) {
      return new File(this.folder, arenaId.toLowerCase() + ".snap");
   }

   boolean hasSnapshot(String arenaId) {
      return this.snapFile(arenaId).isFile();
   }

   int snapshot(String arenaId, CuboidRegion region) {
      if (region == null) {
         return 0;
      } else {
         World world = region.bukkitWorld();
         if (world == null) {
            return 0;
         } else {
            List<ArenaService.BlockEntry> list = new ArrayList<>();

            for (int i = region.minX(); i <= region.maxX(); i++) {
               for (int j = region.minY(); j <= region.maxY(); j++) {
                  for (int k = region.minZ(); k <= region.maxZ(); k++) {
                     Block block = world.getBlockAt(i, j, k);
                     list.add(new ArenaService.BlockEntry(i, j, k, block.getType(), block.getBlockData().getAsString()));
                  }
               }
            }

            File file1 = this.snapFile(arenaId);

            try (DataOutputStream dataoutputstream = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(file1)))) {
               dataoutputstream.writeUTF(world.getName());
               dataoutputstream.writeInt(list.size());

               for (ArenaService.BlockEntry arenaservice$blockentry : list) {
                  dataoutputstream.writeInt(arenaservice$blockentry.x);
                  dataoutputstream.writeInt(arenaservice$blockentry.y);
                  dataoutputstream.writeInt(arenaservice$blockentry.z);
                  dataoutputstream.writeUTF(arenaservice$blockentry.material.name());
                  dataoutputstream.writeUTF(arenaservice$blockentry.blockData);
               }
            } catch (IOException ioexception) {
               this.plugin.getLogger().warning("[arena] Could not save snapshot for " + arenaId + ": " + ioexception.getMessage());
               return 0;
            }

            return list.size();
         }
      }
   }

   void reset(String arenaId, boolean fast, final Runnable onComplete) {
      File file1 = this.snapFile(arenaId);
      if (!file1.isFile()) {
         if (onComplete != null) {
            onComplete.run();
         }
      } else {
         ArenaService.SnapshotData arenaservice$snapshotdata = this.readSnapshot(file1);
         if (arenaservice$snapshotdata != null && !arenaservice$snapshotdata.blocks.isEmpty()) {
            final World world = this.plugin.getServer().getWorld(arenaservice$snapshotdata.worldName);
            if (world == null) {
               if (onComplete != null) {
                  onComplete.run();
               }
            } else {
               final int i = fast ? 8000 : 2000;
               final List<ArenaService.BlockEntry> list = arenaservice$snapshotdata.blocks;
               final BukkitTask[] abukkittask = new BukkitTask[1];
               abukkittask[0] = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, new Runnable() {
                  int index = 0;

                  @Override
                  public void run() {
                     int j = Math.min(this.index + i, list.size());

                     for (int k = this.index; k < j; k++) {
                        ArenaService.BlockEntry arenaservice$blockentry = list.get(k);
                        Block block = world.getBlockAt(arenaservice$blockentry.x, arenaservice$blockentry.y, arenaservice$blockentry.z);
                        block.setType(arenaservice$blockentry.material, false);

                        try {
                           block.setBlockData(ArenaService.this.plugin.getServer().createBlockData(arenaservice$blockentry.blockData), false);
                        } catch (IllegalArgumentException illegalargumentexception) {
                        }
                     }

                     this.index = j;
                     if (this.index >= list.size()) {
                        if (abukkittask[0] != null) {
                           abukkittask[0].cancel();
                        }

                        if (onComplete != null) {
                           onComplete.run();
                        }
                     }
                  }
               }, 0L, 1L);
            }
         } else {
            if (onComplete != null) {
               onComplete.run();
            }
         }
      }
   }

   void resetAll(List<String> arenaIds, boolean fast, Consumer<String> onEachDone) {
      this.resetNext(arenaIds, 0, fast, onEachDone);
   }

   private void resetNext(List<String> arenaIds, int index, boolean fast, Consumer<String> onEachDone) {
      if (index < arenaIds.size()) {
         String s = arenaIds.get(index);
         this.reset(s, fast, () -> {
            if (onEachDone != null) {
               onEachDone.accept(s);
            }

            this.resetNext(arenaIds, index + 1, fast, onEachDone);
         });
      }
   }

   private ArenaService.SnapshotData readSnapshot(File file) {
      List<ArenaService.BlockEntry> list = new ArrayList<>();

      try {
         ArenaService.SnapshotData arenaservice$snapshotdata;
         try (DataInputStream datainputstream = new DataInputStream(new GZIPInputStream(new FileInputStream(file)))) {
            String s = datainputstream.readUTF();
            int i = datainputstream.readInt();

            for (int j = 0; j < i; j++) {
               int k = datainputstream.readInt();
               int l = datainputstream.readInt();
               int i1 = datainputstream.readInt();
               Material material = Material.matchMaterial(datainputstream.readUTF());
               String s1 = datainputstream.readUTF();
               if (material == null) {
                  material = Material.AIR;
               }

               list.add(new ArenaService.BlockEntry(k, l, i1, material, s1));
            }

            arenaservice$snapshotdata = new ArenaService.SnapshotData(s, list);
         }

         return arenaservice$snapshotdata;
      } catch (IOException ioexception) {
         this.plugin.getLogger().warning("[arena] Could not read snapshot " + file.getName() + ": " + ioexception.getMessage());
         return null;
      }
   }

   private static record BlockEntry(int x, int y, int z, Material material, String blockData) {
   }

   private static record SnapshotData(String worldName, List<ArenaService.BlockEntry> blocks) {
   }
}

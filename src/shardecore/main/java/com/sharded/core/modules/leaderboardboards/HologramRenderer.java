package com.sharded.core.modules.leaderboardboards;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class HologramRenderer implements Listener {
   private final ShardedCore plugin;
   private final YamlConfiguration config;
   private final RankingCache cache;
   private final NamespacedKey boardKey;
   private final NamespacedKey lineKey;
   private final NamespacedKey kindKey;
   private final NamespacedKey topperKey;
   private final NamespacedKey topperHeadKey;
   private final NamespacedKey crateHoloKey;
   private final NamespacedKey nametagKey;
   private final Map<String, List<HologramRenderer.LineEntity>> spawned = new ConcurrentHashMap<>();
   private final Map<String, HologramRenderer.SelfLine> selfLines = new ConcurrentHashMap<>();
   private final Map<String, Map<UUID, UUID>> selfEntities = new ConcurrentHashMap<>();

   HologramRenderer(ShardedCore plugin, YamlConfiguration config, RankingCache cache) {
      this.plugin = plugin;
      this.config = config;
      this.cache = cache;
      this.boardKey = new NamespacedKey(plugin, "lb_board");
      this.lineKey = new NamespacedKey(plugin, "lb_line");
      this.kindKey = new NamespacedKey(plugin, "lb_kind");
      this.topperKey = new NamespacedKey(plugin, "lb_topper");
      this.topperHeadKey = new NamespacedKey(plugin, "lb_topper_head");
      this.crateHoloKey = new NamespacedKey(plugin, "crate_holo");
      this.nametagKey = new NamespacedKey(plugin, "stats_nametag");
   }

   void clearWorld() {
      this.purgeTagged(true);
   }

   int purgeTagged(boolean includeLive) {
      int i = 0;

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : List.copyOf(world.getEntities())) {
            if (!this.isProtected(entity) && this.isLeaderboardEntity(entity)) {
               if (!includeLive) {
                  String s = (String)entity.getPersistentDataContainer().get(this.boardKey, PersistentDataType.STRING);
                  if (s != null && this.spawned.containsKey(s)) {
                     continue;
                  }
               }

               entity.remove();
               i++;
            }
         }
      }

      if (includeLive) {
         this.spawned.clear();
         this.clearSelfEntities();
         this.selfLines.clear();
      }

      return i;
   }

   int purgeOrphans(Collection<String> liveIds) {
      int i = 0;

      for (World world : Bukkit.getWorlds()) {
         for (Entity entity : List.copyOf(world.getEntities())) {
            if (!this.isProtected(entity)) {
               PersistentDataContainer persistentdatacontainer = entity.getPersistentDataContainer();
               boolean flag = persistentdatacontainer.has(this.topperKey, PersistentDataType.STRING)
                  || persistentdatacontainer.has(this.topperHeadKey, PersistentDataType.STRING)
                  || persistentdatacontainer.has(this.topperKey, PersistentDataType.BYTE)
                  || persistentdatacontainer.has(this.topperHeadKey, PersistentDataType.BYTE);
               if (flag) {
                  entity.remove();
                  i++;
               } else {
                  String s = (String)persistentdatacontainer.get(this.boardKey, PersistentDataType.STRING);
                  if (s != null) {
                     boolean flag1 = false;
                     Iterator iterator = liveIds.iterator();

                     while (true) {
                        if (iterator.hasNext()) {
                           String s1 = (String)iterator.next();
                           if (!s.equalsIgnoreCase(s1)) {
                              continue;
                           }

                           flag1 = true;
                        }

                        if (!flag1) {
                           entity.remove();
                           i++;
                        }
                        break;
                     }
                  }
               }
            }
         }
      }

      return i;
   }

   int purgeNearby(Location origin, double radius) {
      if (origin != null && origin.getWorld() != null) {
         double d0 = Math.max(1.0, radius);
         int i = 0;

         for (Entity entity : origin.getWorld().getNearbyEntities(origin, d0, d0, d0)) {
            if (!this.isProtected(entity) && (this.isLeaderboardEntity(entity) || this.isOrphanDisplay(entity))) {
               entity.remove();
               i++;
            }
         }

         return i;
      } else {
         return 0;
      }
   }

   private boolean isLeaderboardEntity(Entity entity) {
      PersistentDataContainer persistentdatacontainer = entity.getPersistentDataContainer();
      return persistentdatacontainer.has(this.boardKey, PersistentDataType.STRING)
         || persistentdatacontainer.has(this.lineKey, PersistentDataType.INTEGER)
         || persistentdatacontainer.has(this.kindKey, PersistentDataType.STRING)
         || persistentdatacontainer.has(this.topperKey, PersistentDataType.STRING)
         || persistentdatacontainer.has(this.topperHeadKey, PersistentDataType.STRING)
         || persistentdatacontainer.has(this.topperHeadKey, PersistentDataType.BYTE)
         || persistentdatacontainer.has(this.topperKey, PersistentDataType.BYTE);
   }

   private boolean isProtected(Entity entity) {
      PersistentDataContainer persistentdatacontainer = entity.getPersistentDataContainer();
      return persistentdatacontainer.has(this.crateHoloKey, PersistentDataType.STRING)
         || persistentdatacontainer.has(this.nametagKey, PersistentDataType.STRING);
   }

   private boolean isOrphanDisplay(Entity entity) {
      if (!(entity instanceof Display display)) {
         return false;
      } else {
         return !this.isLeaderboardEntity(display) && !this.isProtected(display) ? display.isInvulnerable() && !display.hasGravity() : false;
      }
   }

   void remove(String boardId) {
      this.removeSelf(boardId);
      List<HologramRenderer.LineEntity> list = this.spawned.remove(boardId);
      if (list != null) {
         for (HologramRenderer.LineEntity hologramrenderer$lineentity : list) {
            hologramrenderer$lineentity.remove();
         }
      }

      for (World world : Bukkit.getWorlds()) {
         for (Display display : world.getEntitiesByClass(Display.class)) {
            String s = (String)display.getPersistentDataContainer().get(this.boardKey, PersistentDataType.STRING);
            if (boardId.equalsIgnoreCase(s)) {
               display.remove();
            }
         }
      }
   }

   void spawn(BoardDefinition board) {
      this.remove(board.id);
      if (board.hologramEnabled && board.placed()) {
         Location location = board.location();
         if (location != null && location.getWorld() != null) {
            if (location.getChunk().isLoaded()) {
               List<String> list = this.displayLines(board);
               List<HologramRenderer.LineEntity> list1 = new ArrayList<>();
               double d0 = board.lineHeight != null ? board.lineHeight : this.config.getDouble("hologram.line-height", 0.26);

               for (int i = 0; i < list.size(); i++) {
                  String s = list.get(i);
                  Location location1 = location.clone().add(0.0, (double)(-i) * d0, 0.0);
                  if (!PlaceholderParser.isSpacer(s)) {
                     if (isSelfLine(s)) {
                        this.selfLines.put(board.id, new HologramRenderer.SelfLine(location1.clone(), s));
                        continue;
                     }
                     PlaceholderParser.LineSpec placeholderparser$linespec = PlaceholderParser.line(s);
                     if (placeholderparser$linespec.hasHead()) {
                        list1.add(this.spawnHead(board, location1, i, placeholderparser$linespec.headRank()));
                     }

                     list1.add(this.spawnText(board, location1, i, placeholderparser$linespec, placeholderparser$linespec.hasHead()));
                  }
               }

               this.spawned.put(board.id, list1);
               this.update(board);
            }
         }
      }
   }

   void update(BoardDefinition board) {
      List<HologramRenderer.LineEntity> list = this.spawned.get(board.id);
      if (list == null) {
         this.spawn(board);
      } else {
         int i = 0;

         for (String s : this.displayLines(board)) {
            if (!PlaceholderParser.isSpacer(s) && !isSelfLine(s)) {
               i++;
               if (PlaceholderParser.line(s).hasHead()) {
                  i++;
               }
            }
         }

         if (list.size() != i) {
            this.spawn(board);
         } else {
            for (HologramRenderer.LineEntity hologramrenderer$lineentity : list) {
               hologramrenderer$lineentity.update(board, this.cache, this.config);
            }
         }
      }
   }

   void ensure(BoardDefinition board, Chunk chunk) {
      if (board.hologramEnabled && board.placed()) {
         Location location = board.location();
         if (location != null && location.getWorld() != null) {
            if (location.getWorld() == chunk.getWorld()) {
               if (location.getBlockX() >> 4 == chunk.getX() && location.getBlockZ() >> 4 == chunk.getZ()) {
                  List<HologramRenderer.LineEntity> list = this.spawned.get(board.id);
                  if (list == null || list.stream().anyMatch(HologramRenderer.LineEntity::dead)) {
                     this.spawn(board);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onChunkLoad(ChunkLoadEvent event) {
   }

   @EventHandler
   public void onWorldLoad(WorldLoadEvent event) {
   }

   @EventHandler
   public void onInteract(PlayerInteractEntityEvent event) {
      Entity entity = event.getRightClicked();
      if (entity.getPersistentDataContainer().has(this.boardKey, PersistentDataType.STRING)) {
         event.setCancelled(true);
      }
   }

   private List<String> displayLines(BoardDefinition board) {
      List<String> list = new ArrayList<>();
      if (board.title != null && !board.title.isBlank()) {
         list.add(board.title);
      }

      if (board.lines != null && !board.lines.isEmpty()) {
         list.addAll(board.lines);
      }

      return list;
   }

   private HologramRenderer.LineEntity spawnText(BoardDefinition board, Location location, int index, PlaceholderParser.LineSpec spec, boolean besideHead) {
      double d0 = this.config.getDouble("hologram.text-gap", 0.22);
      Location locationx = location.clone();
      if (besideHead) {
         locationx.add(this.right(location.getYaw(), d0));
      }

      TextDisplay textdisplay = (TextDisplay)locationx.getWorld().spawn(locationx, TextDisplay.class, entity -> {
         this.mark(entity, board.id, index, "TEXT");
         entity.setPersistent(false);
         entity.setInvulnerable(true);
         entity.setGravity(false);
         entity.setShadowed(true);
         entity.setSeeThrough(false);
         entity.setDefaultBackground(false);
         entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
         entity.setAlignment(TextAlignment.LEFT);
         entity.setLineWidth(this.config.getInt("hologram.line-width", 400));
         entity.setViewRange((float)(this.config.getDouble("hologram.view-range", 48.0) / 64.0));
         entity.setBillboard(this.billboard());
         float f = (float)this.config.getDouble("hologram.scale", 1.0);
         entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(f, f, f), new Quaternionf()));
         entity.setRotation(location.getYaw(), 0.0F);
      });
      return new HologramRenderer.LineEntity(textdisplay.getUniqueId(), "TEXT", index, spec.headRank(), spec.text());
   }

   private HologramRenderer.LineEntity spawnHead(BoardDefinition board, Location location, int index, int rank) {
      double d0 = board.headOffsetX != null ? board.headOffsetX : this.config.getDouble("hologram.head.offset-x", -0.45);
      double d1 = board.headOffsetY != null ? board.headOffsetY : this.config.getDouble("hologram.head.offset-y", 0.08);
      double d2 = board.headOffsetZ != null ? board.headOffsetZ : this.config.getDouble("hologram.head.offset-z", 0.0);
      Location locationx = location.clone().add(this.right(location.getYaw(), d0)).add(0.0, d1, d2);
      float f = (float)(board.headScale != null ? board.headScale : this.config.getDouble("hologram.head.scale", 0.36));
      float f1 = (float)this.config.getDouble("hologram.head.flatness", 0.03);
      ItemDisplay itemdisplay = (ItemDisplay)locationx.getWorld().spawn(locationx, ItemDisplay.class, entity -> {
         this.mark(entity, board.id, index, "PLAYER_HEAD");
         entity.setPersistent(false);
         entity.setInvulnerable(true);
         entity.setGravity(false);
         entity.setItemDisplayTransform(ItemDisplayTransform.FIXED);
         entity.setViewRange((float)(this.config.getDouble("hologram.view-range", 48.0) / 64.0));
         entity.setBillboard(Billboard.FIXED);
         entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(f, f, Math.max(0.01F, f1)), new Quaternionf()));
         entity.setRotation(location.getYaw(), 0.0F);
      });
      return new HologramRenderer.LineEntity(itemdisplay.getUniqueId(), "PLAYER_HEAD", index, rank, "");
   }

   private void mark(Display entity, String boardId, int index, String kind) {
      entity.getPersistentDataContainer().set(this.boardKey, PersistentDataType.STRING, boardId);
      entity.getPersistentDataContainer().set(this.lineKey, PersistentDataType.INTEGER, index);
      entity.getPersistentDataContainer().set(this.kindKey, PersistentDataType.STRING, kind);
   }

   private Billboard billboard() {
      try {
         return Billboard.valueOf(this.config.getString("hologram.billboard", "FIXED").toUpperCase());
      } catch (IllegalArgumentException illegalargumentexception) {
         return Billboard.FIXED;
      }
   }

   private Vector right(float yaw, double distance) {
      double d0 = Math.toRadians((double)yaw);
      return new Vector(-Math.cos(d0) * distance, 0.0, -Math.sin(d0) * distance);
   }

   void tick(Collection<BoardDefinition> boards) {
      for (BoardDefinition board : boards) {
         this.update(board);
         this.updateSelfLine(board);
      }
   }

   private void updateSelfLine(BoardDefinition board) {
      HologramRenderer.SelfLine spec = this.selfLines.get(board.id);
      if (spec == null || spec.location.getWorld() == null) {
         return;
      }
      Map<UUID, UUID> map = this.selfEntities.computeIfAbsent(board.id, ignored -> new ConcurrentHashMap<>());
      Set<UUID> nearby = new HashSet<>();
      for (Player player : spec.location.getWorld().getPlayers()) {
         if (player.getLocation().distanceSquared(spec.location) > 48.0 * 48.0) {
            continue;
         }
         nearby.add(player.getUniqueId());
         UUID entityId = map.get(player.getUniqueId());
         Entity entity = entityId == null ? null : Bukkit.getEntity(entityId);
         if (entity == null || !entity.isValid()) {
            entity = this.spawnPersonalText(board, spec.location, spec.template);
            map.put(player.getUniqueId(), entity.getUniqueId());
         }
         entity.setVisibleByDefault(false);
         player.showEntity(this.plugin, entity);
         if (entity instanceof TextDisplay text) {
            text.text(Text.c(this.resolve(board, spec.template, player)));
         }
      }
      Iterator<Map.Entry<UUID, UUID>> iterator = map.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<UUID, UUID> entry = iterator.next();
         if (nearby.contains(entry.getKey())) {
            continue;
         }
         Entity entity = Bukkit.getEntity(entry.getValue());
         if (entity != null) {
            entity.remove();
         }
         iterator.remove();
      }
   }

   private TextDisplay spawnPersonalText(BoardDefinition board, Location location, String template) {
      return location.getWorld().spawn(location, TextDisplay.class, entity -> {
         this.mark(entity, board.id, 99, "SELF");
         entity.setPersistent(false);
         entity.setInvulnerable(true);
         entity.setGravity(false);
         entity.setShadowed(true);
         entity.setSeeThrough(false);
         entity.setDefaultBackground(false);
         entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
         entity.setAlignment(TextAlignment.LEFT);
         entity.setLineWidth(this.config.getInt("hologram.line-width", 400));
         entity.setViewRange((float)(this.config.getDouble("hologram.view-range", 48.0) / 64.0));
         entity.setBillboard(this.billboard());
         float f = (float)this.config.getDouble("hologram.scale", 1.0);
         entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(f, f, f), new Quaternionf()));
         entity.setRotation(location.getYaw(), 0.0F);
         entity.setVisibleByDefault(false);
         entity.text(Text.c(this.resolve(board, template, null)));
      });
   }

   private void clearSelfEntities() {
      for (Map<UUID, UUID> map : this.selfEntities.values()) {
         for (UUID uuid : map.values()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
               entity.remove();
            }
         }
      }
      this.selfEntities.clear();
   }

   private void removeSelf(String boardId) {
      this.selfLines.remove(boardId);
      Map<UUID, UUID> map = this.selfEntities.remove(boardId);
      if (map != null) {
         for (UUID uuid : map.values()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
               entity.remove();
            }
         }
      }
   }

   static boolean isSelfLine(String template) {
      if (template == null) {
         return false;
      }
      return template.contains("%player_name%")
         || template.contains("_self%")
         || template.contains("_rank%");
   }

   private String resolve(BoardDefinition board, String template) {
      return this.resolve(board, template, this.nearestViewer(board));
   }

   private String resolve(BoardDefinition board, String template, OfflinePlayer viewer) {
      if (template != null && !template.isBlank()) {
         String s = this.config.getString("empty-name", "---");
         String s1 = PlaceholderParser.resolveLine(template, this.cache, viewer, s);

         for (int i = 1; i <= Math.max(board.entries, 10); i++) {
            CachedEntry cachedentry = this.cache.entry(board.statisticKey(), i);
            s1 = s1.replace("%name_" + i + "%", cachedentry.getPlayerName());
            s1 = s1.replace("%value_" + i + "%", cachedentry.getFormattedValue());
            s1 = s1.replace("%uuid_" + i + "%", cachedentry.getPlayerUUID() == null ? "" : cachedentry.getPlayerUUID().toString());
         }

         return s1;
      } else {
         return "";
      }
   }

   private OfflinePlayer nearestViewer(BoardDefinition board) {
      Location location = board.location();
      if (location == null || location.getWorld() == null) {
         return null;
      }
      Player nearest = null;
      double best = 48.0 * 48.0;
      for (Player player : location.getWorld().getPlayers()) {
         double dist = player.getLocation().distanceSquared(location);
         if (dist <= best) {
            best = dist;
            nearest = player;
         }
      }
      return nearest;
   }

   private record SelfLine(Location location, String template) {
   }

   private final class LineEntity {
      private final UUID entityId;
      private final String kind;
      private final int index;
      private final int headRank;
      private final String template;
      private String lastText = "";
      private UUID lastUuid;

      private LineEntity(UUID entityId, String kind, int index, int headRank, String template) {
         this.entityId = entityId;
         this.kind = kind;
         this.index = index;
         this.headRank = headRank;
         this.template = template;
      }

      private boolean dead() {
         Entity entity = Bukkit.getEntity(this.entityId);
         return entity == null || !entity.isValid();
      }

      private void remove() {
         Entity entity = Bukkit.getEntity(this.entityId);
         if (entity != null) {
            entity.remove();
         }
      }

      private void update(BoardDefinition board, RankingCache cache, YamlConfiguration config) {
         Entity entity = Bukkit.getEntity(this.entityId);
         if (entity != null && entity.isValid()) {
            if ("PLAYER_HEAD".equals(this.kind) && entity instanceof ItemDisplay itemdisplay) {
               CachedEntry cachedentry = cache.entry(board.statisticKey(), this.headRank);
               UUID uuid = cachedentry.getPlayerUUID();
               if (uuid == null) {
                  if (this.lastUuid != null) {
                     itemdisplay.setItemStack(PlayerHeads.empty());
                     this.lastUuid = null;
                  }
               } else if (!uuid.equals(this.lastUuid) || itemdisplay.getItemStack() == null || itemdisplay.getItemStack().getType() == Material.AIR) {
                  ItemStack itemstack = cachedentry.getHead();
                  itemdisplay.setItemStack(itemstack);
                  this.lastUuid = uuid;
               }
            } else {
               if (entity instanceof TextDisplay textdisplay) {
                  String s = HologramRenderer.this.resolve(board, this.template);
                  if (s.equals(this.lastText)) {
                     return;
                  }

                  textdisplay.text(Text.c(s));
                  this.lastText = s;
               }
            }
         }
      }
   }
}

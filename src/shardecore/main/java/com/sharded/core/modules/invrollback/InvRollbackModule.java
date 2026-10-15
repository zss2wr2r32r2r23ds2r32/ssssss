package com.sharded.core.modules.invrollback;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public final class InvRollbackModule extends Module implements CommandExecutor, TabCompleter {
   private static final int LIST_PAGE_SIZE = 45;
   private static final int NAV_PREVIOUS = 45;
   private static final int NAV_CLOSE = 49;
   private static final int NAV_NEXT = 53;
   private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
   private static final DateTimeFormatter LIST_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
   private Connection connection;
   private BukkitTask snapshotTask;
   private final Map<UUID, String> knownPlayers = new LinkedHashMap<>();
   private File knownFile;
   private NamespacedKey snapshotKey;

   public InvRollbackModule(ShardedCore plugin) {
      super(plugin, "invrollback");
   }

   @Override
   protected void onEnable() {
      this.snapshotKey = new NamespacedKey(this.plugin, "snapshot_id");
      this.knownFile = new File(this.moduleFolder(), "known-players.yml");
      this.loadKnownPlayers();
      this.openDatabase();
      this.registerListener(this);
      this.registerCommand("invrollback", this);
      long i = Math.max(60L, this.config.getLong("snapshot-interval-minutes", 5L) * 60L * 20L);
      this.snapshotTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::snapshotOnline, i, i);
   }

   @Override
   protected void onDisable() {
      if (this.snapshotTask != null) {
         this.snapshotTask.cancel();
      }

      this.saveKnownPlayers();
      if (this.connection != null) {
         try {
            this.connection.close();
         } catch (SQLException sqlexception) {
         }
      }
   }

   private void openDatabase() {
      try {
         File file1 = new File(this.moduleFolder(), "invrollback.db");
         this.connection = DriverManager.getConnection("jdbc:sqlite:" + file1.getAbsolutePath());

         try (Statement statement = this.connection.createStatement()) {
            statement.execute(
               "CREATE TABLE IF NOT EXISTS snapshots (\n    id INTEGER PRIMARY KEY AUTOINCREMENT,\n    uuid TEXT NOT NULL,\n    reason TEXT NOT NULL,\n    created_at INTEGER NOT NULL,\n    contents BLOB NOT NULL,\n    armor BLOB,\n    offhand BLOB\n)\n"
            );
            statement.execute("PRAGMA journal_mode=WAL");
         }
      } catch (SQLException sqlexception) {
         throw new IllegalStateException("Could not open invrollback database", sqlexception);
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!this.canUse(sender)) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (sender instanceof Player player) {
         if (args.length == 0) {
            this.send(player, "usage", new String[0]);
            return true;
         } else {
            OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
            if (offlineplayer != null && this.isKnown(offlineplayer)) {
               this.openCategories(player, offlineplayer.getUniqueId(), this.displayName(offlineplayer));
               return true;
            } else {
               this.send(player, "never-joined", new String[]{"%player%", args[0]});
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!this.canUse(sender)) {
         return List.of();
      } else {
         return args.length == 1 ? TabCompleteHelper.filter(args[0], this.knownNames()) : List.of();
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onLogin(PlayerLoginEvent event) {
      this.remember(event.getPlayer());
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.remember(event.getPlayer());
      if (this.config.getBoolean("snapshot-on.join", true)) {
         this.scheduleSnapshot(event.getPlayer(), InvRollbackModule.SnapshotReason.JOIN);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onQuit(PlayerQuitEvent event) {
      if (this.config.getBoolean("snapshot-on.quit", true)) {
         this.scheduleSnapshot(event.getPlayer(), InvRollbackModule.SnapshotReason.QUIT);
      }
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onDeath(PlayerDeathEvent event) {
      if (this.config.getBoolean("snapshot-on.death", true)) {
         this.scheduleSnapshot(event.getEntity(), InvRollbackModule.SnapshotReason.DEATH);
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         InvRollbackModule.Holder invrollbackmodule$holder = TrackedInventories.lookup(event.getView().getTopInventory(), InvRollbackModule.Holder.class);
         if (invrollbackmodule$holder != null) {
            boolean flag = event.getClickedInventory() == event.getView().getTopInventory();
            boolean flag1 = invrollbackmodule$holder.type() == InvRollbackModule.Holder.Type.PREVIEW && this.config.getBoolean("preview.take-items", true);
            if (!flag) {
               if (!flag1) {
                  event.setCancelled(true);
               }
            } else if (!flag1 || this.isPreviewActionSlot(event.getSlot())) {
               event.setCancelled(true);
               switch (invrollbackmodule$holder.type()) {
                  case CATEGORIES:
                     this.handleCategoryClick(player, invrollbackmodule$holder, event.getSlot());
                     break;
                  case LIST:
                     this.handleListClick(player, invrollbackmodule$holder, event.getSlot());
                     break;
                  case PREVIEW:
                     this.handlePreviewClick(player, invrollbackmodule$holder, event.getSlot());
               }
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      TrackedInventories.untrack(event.getInventory(), InvRollbackModule.Holder.class);
   }

   private void handleCategoryClick(Player staff, InvRollbackModule.Holder holder, int slot) {
      InvRollbackModule.SnapshotReason invrollbackmodule$snapshotreason = this.categoryFromSlot(slot);
      if (invrollbackmodule$snapshotreason != null) {
         this.openSnapshotList(staff, holder.targetId(), holder.targetName(), invrollbackmodule$snapshotreason, 0, true);
      }
   }

   private void handleListClick(Player staff, InvRollbackModule.Holder holder, int slot) {
      if (slot == 45 && holder.page() > 0) {
         this.openSnapshotList(staff, holder.targetId(), holder.targetName(), holder.reason(), holder.page() - 1);
      } else if (slot == 53) {
         List<InvRollbackModule.Snapshot> list = this.list(holder.targetId(), holder.reason());
         int j = Math.max(0, (list.size() - 1) / 45);
         if (holder.page() < j) {
            this.openSnapshotList(staff, holder.targetId(), holder.targetName(), holder.reason(), holder.page() + 1);
         }
      } else if (slot == 49) {
         staff.closeInventory();
      } else {
         ItemStack itemstack = staff.getOpenInventory().getTopInventory().getItem(slot);
         if (itemstack != null && itemstack.hasItemMeta()) {
            long i = (Long)itemstack.getItemMeta().getPersistentDataContainer().getOrDefault(this.snapshotKey, PersistentDataType.LONG, -1L);
            if (i >= 0L) {
               InvRollbackModule.Snapshot invrollbackmodule$snapshot = this.load(i);
               if (invrollbackmodule$snapshot == null) {
                  this.send(staff, "empty-backup", new String[0]);
               } else {
                  this.openPreview(staff, holder.targetId(), holder.targetName(), holder.reason(), holder.page(), invrollbackmodule$snapshot);
               }
            }
         }
      }
   }

   private void handlePreviewClick(Player staff, InvRollbackModule.Holder holder, int slot) {
      InvRollbackModule.Snapshot invrollbackmodule$snapshot = holder.snapshot();
      if (invrollbackmodule$snapshot != null) {
         if (slot == this.navSlot("preview.back", 53)) {
            this.openSnapshotList(staff, holder.targetId(), holder.targetName(), holder.reason(), holder.page());
         } else if (slot == this.navSlot("preview.restore", 48)) {
            this.restore(staff, invrollbackmodule$snapshot);
         } else if (slot == this.navSlot("preview.to-inventory", 49)) {
            this.givePackedShulker(staff, invrollbackmodule$snapshot, false);
         } else if (slot == this.navSlot("preview.to-enderchest", 50)) {
            this.givePackedShulker(staff, invrollbackmodule$snapshot, true);
         }
      }
   }

   private boolean isPreviewActionSlot(int slot) {
      return slot == this.navSlot("preview.restore", 48)
         || slot == this.navSlot("preview.to-inventory", 49)
         || slot == this.navSlot("preview.to-enderchest", 50)
         || slot == this.navSlot("preview.back", 53);
   }

   private int navSlot(String path, int def) {
      return this.config.getInt(path + ".slot", def);
   }

   private void snapshotOnline() {
      int i = this.config.getInt("snapshots-per-tick", 5);
      int j = 0;

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (j++ >= i) {
            break;
         }

         this.scheduleSnapshot(player, InvRollbackModule.SnapshotReason.AUTO);
      }
   }

   private void openCategories(Player staff, UUID targetId, String targetName) {
      Player player = Bukkit.getPlayer(targetId);
      if (player != null) {
         this.scheduleSnapshot(player, InvRollbackModule.SnapshotReason.AUTO);
      }

      int i = this.config.getInt("categories.size", 27);
      InvRollbackModule.Holder invrollbackmodule$holder = new InvRollbackModule.Holder(
         InvRollbackModule.Holder.Type.CATEGORIES, targetId, targetName, null, 0, null
      );
      Inventory inventory = Bukkit.createInventory(
         invrollbackmodule$holder, i, Text.c(this.apply(this.config.getString("categories.title", "&8Rollback | %player%"), "%player%", targetName))
      );
      invrollbackmodule$holder.bind(inventory);
      TrackedInventories.track(inventory, invrollbackmodule$holder);
      this.putCategory(inventory, "auto", InvRollbackModule.SnapshotReason.AUTO, targetId);
      this.putCategory(inventory, "join", InvRollbackModule.SnapshotReason.JOIN, targetId);
      this.putCategory(inventory, "quit", InvRollbackModule.SnapshotReason.QUIT, targetId);
      this.putCategory(inventory, "death", InvRollbackModule.SnapshotReason.DEATH, targetId);
      staff.openInventory(inventory);
   }

   private void putCategory(Inventory inv, String key, InvRollbackModule.SnapshotReason reason, UUID uuid) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("categories.items." + key);
      if (configurationsection != null) {
         inv.setItem(
            configurationsection.getInt("slot", 0),
            this.buildConfigItem(configurationsection, Map.of("%count%", String.valueOf(this.count(uuid, reason)), "%player%", OfflinePlayers.name(uuid)))
         );
      }
   }

   private void openSnapshotList(Player staff, UUID targetId, String targetName, InvRollbackModule.SnapshotReason reason, int page) {
      this.openSnapshotList(staff, targetId, targetName, reason, page, false);
   }

   private void openSnapshotList(Player staff, UUID targetId, String targetName, InvRollbackModule.SnapshotReason reason, int page, boolean fromCategory) {
      List<InvRollbackModule.Snapshot> list = this.list(targetId, reason);
      if (list.isEmpty()) {
         this.send(staff, "none", new String[]{"%player%", targetName, "%reason%", reason.name().toLowerCase(Locale.ROOT)});
         if (fromCategory) {
            this.openCategories(staff, targetId, targetName);
         }
      } else {
         int i = Math.max(0, (list.size() - 1) / 45);
         page = Math.max(0, Math.min(page, i));
         InvRollbackModule.Holder invrollbackmodule$holder = new InvRollbackModule.Holder(
            InvRollbackModule.Holder.Type.LIST, targetId, targetName, reason, page, null
         );
         Inventory inventory = Bukkit.createInventory(
            invrollbackmodule$holder, 54, Text.c(this.apply(this.config.getString("title", "&8Inventory Rollback | %player%"), "%player%", targetName))
         );
         invrollbackmodule$holder.bind(inventory);
         TrackedInventories.track(inventory, invrollbackmodule$holder);
         ItemStack itemstack = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

         for (int j = 45; j < 54; j++) {
            if (j != 45 && j != 49 && j != 53) {
               inventory.setItem(j, itemstack);
            }
         }

         int j1 = page * 45;
         int k = Math.min(j1 + 45, list.size());
         int l = 0;

         for (int i1 = j1; i1 < k; i1++) {
            InvRollbackModule.Snapshot invrollbackmodule$snapshot = list.get(i1);
            String s = LIST_TIME.format(Instant.ofEpochMilli(invrollbackmodule$snapshot.createdAt()));
            ItemStack itemstack1 = this.buildConfigItem(
               this.config.getConfigurationSection("item"),
               Map.of(
                  "%date%",
                  s,
                  "%reason%",
                  invrollbackmodule$snapshot.reason().name().toLowerCase(Locale.ROOT),
                  "%items%",
                  String.valueOf(this.countItems(invrollbackmodule$snapshot)),
                  "%time%",
                  TIME.format(Instant.ofEpochMilli(invrollbackmodule$snapshot.createdAt())),
                  "%player%",
                  targetName
               )
            );
            if (itemstack1.getType().isAir()) {
               itemstack1 = new ItemBuilder(Material.CHEST).build();
            }

            itemstack1.editMeta(meta -> meta.getPersistentDataContainer().set(this.snapshotKey, PersistentDataType.LONG, invrollbackmodule$snapshot.id()));
            inventory.setItem(l++, itemstack1);
         }

         if (page > 0) {
            inventory.setItem(45, this.buildNavItem("navigation.previous"));
         }

         inventory.setItem(49, this.buildNavItem("navigation.close"));
         if (page < i) {
            inventory.setItem(53, this.buildNavItem("navigation.next"));
         }

         staff.openInventory(inventory);
      }
   }

   private void openPreview(Player staff, UUID targetId, String targetName, InvRollbackModule.SnapshotReason reason, int page, InvRollbackModule.Snapshot snap) {
      InvRollbackModule.Holder invrollbackmodule$holder = new InvRollbackModule.Holder(
         InvRollbackModule.Holder.Type.PREVIEW, targetId, targetName, reason, page, snap
      );
      Inventory inventory = Bukkit.createInventory(
         invrollbackmodule$holder, 54, Text.c(this.apply(this.config.getString("preview.title", "&8Preview | %player%"), "%player%", targetName))
      );
      invrollbackmodule$holder.bind(inventory);
      TrackedInventories.track(inventory, invrollbackmodule$holder);
      ItemStack[] aitemstack = snap.contents() == null ? new ItemStack[0] : snap.contents();

      for (int i = 0; i < Math.min(36, aitemstack.length); i++) {
         ItemStack itemstack = aitemstack[i];
         if (itemstack != null && !itemstack.getType().isAir()) {
            inventory.setItem(i, itemstack.clone());
         }
      }

      ItemStack[] aitemstack1 = snap.armor() == null ? new ItemStack[0] : snap.armor();
      if (aitemstack1.length > 0 && aitemstack1[0] != null) {
         inventory.setItem(36, aitemstack1[0].clone());
      }

      if (aitemstack1.length > 1 && aitemstack1[1] != null) {
         inventory.setItem(37, aitemstack1[1].clone());
      }

      if (aitemstack1.length > 2 && aitemstack1[2] != null) {
         inventory.setItem(38, aitemstack1[2].clone());
      }

      if (aitemstack1.length > 3 && aitemstack1[3] != null) {
         inventory.setItem(39, aitemstack1[3].clone());
      }

      if (snap.offhand() != null && snap.offhand().length > 0 && snap.offhand()[0] != null) {
         inventory.setItem(40, snap.offhand()[0].clone());
      }

      ItemStack itemstack1 = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

      for (int j = 41; j < 54; j++) {
         if (!this.isPreviewActionSlot(j)) {
            inventory.setItem(j, itemstack1);
         }
      }

      this.putPreviewButton(inventory, "preview.restore");
      this.putPreviewButton(inventory, "preview.to-inventory");
      this.putPreviewButton(inventory, "preview.to-enderchest");
      this.putPreviewButton(inventory, "preview.back");
      staff.openInventory(inventory);
   }

   private void putPreviewButton(Inventory inv, String path) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection(path);
      if (configurationsection != null) {
         inv.setItem(configurationsection.getInt("slot", 0), this.buildConfigItem(configurationsection, Map.of()));
      }
   }

   private ItemStack buildNavItem(String path) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection(path);
      return configurationsection == null
         ? new ItemBuilder(Material.BARRIER).name("&cMissing: " + path).build()
         : this.buildConfigItem(configurationsection, Map.of());
   }

   private ItemStack buildConfigItem(ConfigurationSection section, Map<String, String> placeholders) {
      if (section == null) {
         return new ItemStack(Material.AIR);
      } else {
         Material material = Material.matchMaterial(section.getString("material", "CHEST"));
         if (material == null) {
            material = Material.CHEST;
         }

         List<String> list = new ArrayList<>();

         for (String s : section.getStringList("lore")) {
            list.add(this.apply(s, placeholders));
         }

         return new ItemBuilder(material).name(this.apply(section.getString("display_name", " "), placeholders)).lore(list).hideAll().build();
      }
   }

   private String apply(String input, Map<String, String> placeholders) {
      if (input == null) {
         return "";
      } else {
         String s = input;

         for (Entry<String, String> entry : placeholders.entrySet()) {
            s = s.replace(entry.getKey(), (CharSequence)(entry.getValue() == null ? "" : entry.getValue()));
         }

         return s;
      }
   }

   private String apply(String input, String... pairs) {
      Map<String, String> map = new HashMap<>();

      for (int i = 0; i + 1 < pairs.length; i += 2) {
         map.put(pairs[i], pairs[i + 1]);
      }

      return this.apply(input, map);
   }

   private InvRollbackModule.SnapshotReason categoryFromSlot(int slot) {
      for (String s : List.of("auto", "join", "quit", "death")) {
         ConfigurationSection configurationsection = this.config.getConfigurationSection("categories.items." + s);
         if (configurationsection != null && configurationsection.getInt("slot") == slot) {
            return InvRollbackModule.SnapshotReason.valueOf(s.toUpperCase(Locale.ROOT));
         }
      }

      return null;
   }

   private void restore(Player staff, InvRollbackModule.Snapshot snap) {
      Player player = Bukkit.getPlayer(snap.uuid());
      if (player != null && player.isOnline()) {
         player.getInventory().setContents(this.clone(snap.contents()));
         player.getInventory().setArmorContents(this.clone(snap.armor()));
         if (snap.offhand() != null && snap.offhand().length > 0) {
            player.getInventory().setItemInOffHand(snap.offhand()[0]);
         }

         player.updateInventory();
         this.send(
            staff,
            "restored",
            new String[]{
               "%player%",
               player.getName(),
               "%when%",
               TIME.format(Instant.ofEpochMilli(snap.createdAt())),
               "%reason%",
               snap.reason().name().toLowerCase(Locale.ROOT)
            }
         );
         if (!staff.equals(player)) {
            this.send(player, "restored-target", new String[]{"%staff%", staff.getName(), "%when%", TIME.format(Instant.ofEpochMilli(snap.createdAt()))});
         }
      } else {
         this.send(staff, "offline", new String[]{"%player%", this.displayName(snap.uuid())});
      }
   }

   private void givePackedShulker(Player staff, InvRollbackModule.Snapshot snap, boolean enderChest) {
      Player player = Bukkit.getPlayer(snap.uuid());
      if (player != null && player.isOnline()) {
         List<ItemStack> list = this.snapshotItems(snap);
         if (list.isEmpty()) {
            this.send(staff, "empty-backup", new String[0]);
         } else {
            while (!list.isEmpty()) {
               ItemStack itemstack = this.packShulker(list);
               if (itemstack == null) {
                  break;
               }

               if (enderChest) {
                  if (!this.placeInEnderChest(player, itemstack)) {
                     player.getInventory().addItem(new ItemStack[]{itemstack});
                  }
               } else {
                  player.getInventory().addItem(new ItemStack[]{itemstack});
               }
            }

            this.send(staff, enderChest ? "packed-ender" : "packed-inv", new String[]{"%player%", player.getName()});
         }
      } else {
         this.send(staff, "offline", new String[]{"%player%", this.displayName(snap.uuid())});
      }
   }

   private List<ItemStack> snapshotItems(InvRollbackModule.Snapshot snap) {
      List<ItemStack> list = new ArrayList<>();
      if (snap.contents() != null) {
         for (ItemStack itemstack : snap.contents()) {
            if (itemstack != null && !itemstack.getType().isAir()) {
               list.add(itemstack.clone());
            }
         }
      }

      if (snap.armor() != null) {
         for (ItemStack itemstack1 : snap.armor()) {
            if (itemstack1 != null && !itemstack1.getType().isAir()) {
               list.add(itemstack1.clone());
            }
         }
      }

      if (snap.offhand() != null) {
         for (ItemStack itemstack2 : snap.offhand()) {
            if (itemstack2 != null && !itemstack2.getType().isAir()) {
               list.add(itemstack2.clone());
            }
         }
      }

      return list;
   }

   private ItemStack packShulker(List<ItemStack> items) {
      ItemStack itemstack = new ItemStack(Material.SHULKER_BOX);
      BlockStateMeta blockstatemeta = (BlockStateMeta)itemstack.getItemMeta();
      if (blockstatemeta != null && blockstatemeta.getBlockState() instanceof ShulkerBox shulkerbox) {
         int i = 0;

         while (!items.isEmpty() && i < shulkerbox.getInventory().getSize()) {
            shulkerbox.getInventory().setItem(i++, items.removeFirst());
         }

         blockstatemeta.setBlockState(shulkerbox);
         itemstack.setItemMeta(blockstatemeta);
         return itemstack;
      } else {
         return null;
      }
   }

   private boolean placeInEnderChest(Player target, ItemStack shulker) {
      Inventory inventory = target.getEnderChest();

      for (int i = 0; i < inventory.getSize(); i++) {
         ItemStack itemstack = inventory.getItem(i);
         if (itemstack == null || itemstack.getType().isAir()) {
            inventory.setItem(i, shulker);
            return true;
         }
      }

      return false;
   }

   private int countItems(InvRollbackModule.Snapshot snap) {
      int i = 0;
      if (snap.contents() != null) {
         for (ItemStack itemstack : snap.contents()) {
            if (itemstack != null && !itemstack.getType().isAir()) {
               i++;
            }
         }
      }

      if (snap.armor() != null) {
         for (ItemStack itemstack1 : snap.armor()) {
            if (itemstack1 != null && !itemstack1.getType().isAir()) {
               i++;
            }
         }
      }

      if (snap.offhand() != null) {
         for (ItemStack itemstack2 : snap.offhand()) {
            if (itemstack2 != null && !itemstack2.getType().isAir()) {
               i++;
            }
         }
      }

      return i;
   }

   private void remember(Player player) {
      this.knownPlayers.put(player.getUniqueId(), player.getName());
      this.saveKnownPlayers();
   }

   private boolean isKnown(OfflinePlayer target) {
      if (target.isOnline()) {
         return true;
      } else {
         return this.knownPlayers.containsKey(target.getUniqueId()) ? true : target.hasPlayedBefore();
      }
   }

   private List<String> knownNames() {
      List<String> list = new ArrayList<>(this.knownPlayers.values());

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!list.contains(player.getName())) {
            list.add(player.getName());
         }
      }

      list.sort(String.CASE_INSENSITIVE_ORDER);
      return list;
   }

   private String displayName(OfflinePlayer target) {
      return target.getName() != null ? target.getName() : this.knownPlayers.getOrDefault(target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()));
   }

   private String displayName(UUID uuid) {
      OfflinePlayer offlineplayer = Bukkit.getOfflinePlayer(uuid);
      return this.displayName(offlineplayer);
   }

   private void loadKnownPlayers() {
      this.knownPlayers.clear();
      if (this.knownFile.exists()) {
         YamlConfiguration yamlconfiguration = YamlConfiguration.loadConfiguration(this.knownFile);
         ConfigurationSection configurationsection = yamlconfiguration.getConfigurationSection("players");
         if (configurationsection != null) {
            for (String s : configurationsection.getKeys(false)) {
               try {
                  this.knownPlayers.put(UUID.fromString(s), configurationsection.getString(s, s));
               } catch (IllegalArgumentException illegalargumentexception) {
               }
            }

            Bukkit.getOnlinePlayers().forEach(player -> this.knownPlayers.put(player.getUniqueId(), player.getName()));
         }
      }
   }

   private void saveKnownPlayers() {
      YamlConfiguration yamlconfiguration = new YamlConfiguration();
      this.knownPlayers.forEach((uuid, name) -> yamlconfiguration.set("players." + uuid, name));

      try {
         yamlconfiguration.save(this.knownFile);
      } catch (IOException ioexception) {
         this.plugin.getLogger().warning("[invrollback] Could not save known-players.yml");
      }
   }

   private boolean canUse(CommandSender sender) {
      return sender.hasPermission("sharded.invrollback.use") || sender.hasPermission("sharded.staff.invrollback");
   }

   private void scheduleSnapshot(Player player, InvRollbackModule.SnapshotReason reason) {
      UUID uuid = player.getUniqueId();
      ItemStack[] aitemstack = this.clone(player.getInventory().getContents());
      ItemStack[] aitemstack1 = this.clone(player.getInventory().getArmorContents());
      ItemStack[] aitemstack2 = this.clone(new ItemStack[]{player.getInventory().getItemInOffHand()});
      this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> this.persistSnapshot(uuid, reason, aitemstack, aitemstack1, aitemstack2));
   }

   private void persistSnapshot(UUID uuid, InvRollbackModule.SnapshotReason reason, ItemStack[] contents, ItemStack[] armor, ItemStack[] offhand) {
      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("INSERT INTO snapshots (uuid, reason, created_at, contents, armor, offhand) VALUES (?, ?, ?, ?, ?, ?)")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, reason.name());
         preparedstatement.setLong(3, System.currentTimeMillis());
         preparedstatement.setBytes(4, this.serialize(contents));
         preparedstatement.setBytes(5, this.serialize(armor));
         preparedstatement.setBytes(6, this.serialize(offhand));
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
         this.plugin.getLogger().warning("[invrollback] save failed: " + sqlexception.getMessage());
      }

      this.trim(uuid);
   }

   private void trim(UUID uuid) {
      int i = Math.max(1, this.config.getInt("max-snapshots", 20));

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement(
               "DELETE FROM snapshots WHERE uuid = ? AND id NOT IN (\n    SELECT id FROM snapshots WHERE uuid = ? ORDER BY created_at DESC LIMIT ?\n)\n"
            )) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, uuid.toString());
         preparedstatement.setInt(3, i);
         preparedstatement.executeUpdate();
      } catch (SQLException sqlexception) {
      }
   }

   private int count(UUID uuid, InvRollbackModule.SnapshotReason reason) {
      try {
         int i;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT COUNT(*) FROM snapshots WHERE uuid = ? AND reason = ?")) {
            preparedstatement.setString(1, uuid.toString());
            preparedstatement.setString(2, reason.name());

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               i = resultset.next() ? resultset.getInt(1) : 0;
            }
         }

         return i;
      } catch (SQLException sqlexception) {
         return 0;
      }
   }

   private List<InvRollbackModule.Snapshot> list(UUID uuid, InvRollbackModule.SnapshotReason reason) {
      List<InvRollbackModule.Snapshot> list = new ArrayList<>();
      int i = Math.max(1, this.config.getInt("max-snapshots", 20));

      try (PreparedStatement preparedstatement = this.connection
            .prepareStatement("SELECT * FROM snapshots WHERE uuid = ? AND reason = ? ORDER BY created_at DESC LIMIT ?")) {
         preparedstatement.setString(1, uuid.toString());
         preparedstatement.setString(2, reason.name());
         preparedstatement.setInt(3, i);

         try (ResultSet resultset = preparedstatement.executeQuery()) {
            while (resultset.next()) {
               list.add(this.read(resultset));
            }
         }
      } catch (SQLException sqlexception) {
      }

      return list;
   }

   private InvRollbackModule.Snapshot load(long id) {
      try {
         InvRollbackModule.Snapshot invrollbackmodule$snapshot;
         try (PreparedStatement preparedstatement = this.connection.prepareStatement("SELECT * FROM snapshots WHERE id = ?")) {
            preparedstatement.setLong(1, id);

            try (ResultSet resultset = preparedstatement.executeQuery()) {
               invrollbackmodule$snapshot = resultset.next() ? this.read(resultset) : null;
            }
         }

         return invrollbackmodule$snapshot;
      } catch (SQLException sqlexception) {
         return null;
      }
   }

   private InvRollbackModule.Snapshot read(ResultSet rs) throws SQLException {
      return new InvRollbackModule.Snapshot(
         rs.getLong("id"),
         UUID.fromString(rs.getString("uuid")),
         InvRollbackModule.SnapshotReason.valueOf(rs.getString("reason")),
         rs.getLong("created_at"),
         this.deserialize(rs.getBytes("contents")),
         this.deserialize(rs.getBytes("armor")),
         this.deserialize(rs.getBytes("offhand"))
      );
   }

   private byte[] serialize(ItemStack[] items) {
      try {
         byte[] abyte;
         try (ByteArrayOutputStream bytearrayoutputstream = new ByteArrayOutputStream()) {
            BukkitObjectOutputStream bukkitobjectoutputstream = new BukkitObjectOutputStream(bytearrayoutputstream);

            try {
               bukkitobjectoutputstream.writeInt(items.length);

               for (ItemStack itemstack : items) {
                  bukkitobjectoutputstream.writeObject(itemstack);
               }

               abyte = bytearrayoutputstream.toByteArray();
            } catch (Throwable throwable1) {
               try {
                  bukkitobjectoutputstream.close();
               } catch (Throwable throwable) {
                  throwable1.addSuppressed(throwable);
               }

               throw throwable1;
            }

            bukkitobjectoutputstream.close();
         }

         return abyte;
      } catch (Exception exception) {
         return new byte[0];
      }
   }

   private ItemStack[] deserialize(byte[] data) {
      if (data != null && data.length != 0) {
         try {
            BukkitObjectInputStream bukkitobjectinputstream = new BukkitObjectInputStream(new ByteArrayInputStream(data));

            ItemStack[] aitemstack1;
            try {
               int i = bukkitobjectinputstream.readInt();
               ItemStack[] aitemstack = new ItemStack[i];

               for (int j = 0; j < i; j++) {
                  aitemstack[j] = (ItemStack)bukkitobjectinputstream.readObject();
               }

               aitemstack1 = aitemstack;
            } catch (Throwable throwable1) {
               try {
                  bukkitobjectinputstream.close();
               } catch (Throwable throwable) {
                  throwable1.addSuppressed(throwable);
               }

               throw throwable1;
            }

            bukkitobjectinputstream.close();
            return aitemstack1;
         } catch (Exception exception) {
            return new ItemStack[0];
         }
      } else {
         return new ItemStack[0];
      }
   }

   private ItemStack[] clone(ItemStack[] items) {
      ItemStack[] aitemstack = new ItemStack[items.length];

      for (int i = 0; i < items.length; i++) {
         aitemstack[i] = items[i] == null ? null : items[i].clone();
      }

      return aitemstack;
   }

   private static final class Holder implements InventoryHolder {
      private final InvRollbackModule.Holder.Type type;
      private final UUID targetId;
      private final String targetName;
      private final InvRollbackModule.SnapshotReason reason;
      private final int page;
      private final InvRollbackModule.Snapshot snapshot;
      private Inventory inventory;

      Holder(
         InvRollbackModule.Holder.Type type,
         UUID targetId,
         String targetName,
         InvRollbackModule.SnapshotReason reason,
         int page,
         InvRollbackModule.Snapshot snapshot
      ) {
         this.type = type;
         this.targetId = targetId;
         this.targetName = targetName;
         this.reason = reason;
         this.page = page;
         this.snapshot = snapshot;
      }

      void bind(Inventory inventory) {
         this.inventory = inventory;
      }

      InvRollbackModule.Holder.Type type() {
         return this.type;
      }

      UUID targetId() {
         return this.targetId;
      }

      String targetName() {
         return this.targetName;
      }

      InvRollbackModule.SnapshotReason reason() {
         return this.reason;
      }

      int page() {
         return this.page;
      }

      InvRollbackModule.Snapshot snapshot() {
         return this.snapshot;
      }

      public Inventory getInventory() {
         return this.inventory;
      }

      static enum Type {
         CATEGORIES,
         LIST,
         PREVIEW;
      }
   }

   public static record Snapshot(
      long id, UUID uuid, InvRollbackModule.SnapshotReason reason, long createdAt, ItemStack[] contents, ItemStack[] armor, ItemStack[] offhand
   ) {
   }

   public static enum SnapshotReason {
      AUTO,
      JOIN,
      QUIT,
      DEATH;
   }
}

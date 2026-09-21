package com.sharded.core.modules.spawnselect;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.LocationUtil;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class SpawnSelectModule extends Module implements CommandExecutor, TabCompleter {
   private static final String STATE_KEY = "spawn-selection";
   private final Map<UUID, BukkitTask> pendingTeleports = new HashMap<>();
   private File guiFile;

   public SpawnSelectModule(ShardedCore plugin) {
      super(plugin, "spawnselect");
   }

   @Override
   protected void onEnable() {
      this.guiFile = this.syncJarResource("gui.yml");
      if (this.plugin.gui() != null) {
         if (this.guiFile.exists()) {
            this.plugin.gui().loadMenu(this.guiFile, "spawnselect");
         }

         this.plugin.gui().registerAction("spawn_select_main", player -> this.select(player, "main"));
         this.plugin.gui().registerAction("spawn_select_vanilla", player -> this.select(player, "vanilla"));
      }

      this.registerCommand("spawn", this);
      this.registerCommand("spawnselect", this);
      this.registerCommand("spawnselector", this);
      this.registerCommand("setspawn", this);
   }

   @Override
   protected void onDisable() {
      for (BukkitTask bukkittask : this.pendingTeleports.values()) {
         bukkittask.cancel();
      }

      this.pendingTeleports.clear();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("setspawn")) {
         return this.handleSetSpawn(sender, args);
      } else if (sender instanceof Player player) {
         if (!s.equals("spawn")) {
            this.openSelector(player);
            return true;
         } else {
            return this.handleSpawn(player, args);
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean handleSpawn(Player player, String[] args) {
      if (args.length == 0) {
         if (!this.config.getBoolean("spawn-command-enabled", true)) {
            this.send(player, "spawn-disabled", new String[0]);
            return true;
         } else {
            Location location = this.mainSpawn();
            if (location == null) {
               this.send(player, "main-not-set", new String[0]);
               return true;
            } else {
               this.beginCountdown(player, location);
               return true;
            }
         }
      } else if (args.length == 1 && args[0].equalsIgnoreCase("select")) {
         this.openSelector(player);
         return true;
      } else {
         String s = null;
         if (args.length == 1 && this.isSelection(args[0])) {
            s = args[0];
         } else if (args.length == 2 && args[0].equalsIgnoreCase("select") && this.isSelection(args[1])) {
            s = args[1];
         }

         if (s != null) {
            this.select(player, s);
            return true;
         } else {
            this.send(player, "usage", new String[0]);
            return true;
         }
      }
   }

   private boolean isSelection(String value) {
      return value.equalsIgnoreCase("main") || value.equalsIgnoreCase("vanilla");
   }

   private void select(Player player, String selection) {
      String s = selection.toLowerCase(Locale.ROOT);
      if (s.equals("main") && this.mainSpawn() == null) {
         this.send(player, "main-not-set", new String[0]);
      } else if (s.equals("vanilla") && this.vanillaWorld() == null) {
         this.send(player, "vanilla-not-set", new String[0]);
      } else {
         this.plugin.stateStore().setString(player.getUniqueId(), "spawn-selection", s);
         this.send(player, s.equals("main") ? "selected-main" : "selected-vanilla", new String[0]);
      }
   }

   private void beginCountdown(final Player player, final Location target) {
      this.cancelPending(player, false);
      final int i = Math.max(0, this.config.getInt("main-teleport-seconds", 10));
      if (i == 0) {
         this.teleportToMain(player, target);
      } else {
         BukkitRunnable bukkitrunnable = new BukkitRunnable() {
            private int remaining = i;

            public void run() {
               if (!player.isOnline()) {
                  SpawnSelectModule.this.pendingTeleports.remove(player.getUniqueId());
                  this.cancel();
               } else if (this.remaining <= 0) {
                  SpawnSelectModule.this.pendingTeleports.remove(player.getUniqueId());
                  this.cancel();
                  SpawnSelectModule.this.teleportToMain(player, target);
               } else {
                  String s = SpawnSelectModule.this.config.getString("main-teleport-actionbar", "&eTeleporting to spawn in &f%seconds%&es");
                  player.sendActionBar(Text.c(Text.apply(s, "%seconds%", String.valueOf(this.remaining))));
                  SpawnSelectModule.this.playSound(player, SpawnSelectModule.this.config.getString("countdown-sound", "BLOCK_NOTE_BLOCK_PLING"));
                  this.remaining--;
               }
            }
         };
         BukkitTask bukkittask = bukkitrunnable.runTaskTimer(this.plugin, 0L, 20L);
         this.pendingTeleports.put(player.getUniqueId(), bukkittask);
      }
   }

   private void teleportToMain(Player player, Location target) {
      if (player.teleport(target)) {
         this.send(player, "teleported-main", new String[0]);
      }
   }

   private void cancelPending(Player player, boolean notify) {
      BukkitTask bukkittask = this.pendingTeleports.remove(player.getUniqueId());
      if (bukkittask != null) {
         bukkittask.cancel();
         if (notify) {
            String s = this.config.getString("teleport-cancelled-actionbar", "&cTeleport cancelled because you moved.");
            player.sendActionBar(Text.c(s));
            this.send(player, "teleport-cancelled", new String[0]);
            this.playSound(player, this.config.getString("cancel-sound", "BLOCK_NOTE_BLOCK_BASS"));
         }
      }
   }

   @EventHandler(
      ignoreCancelled = true
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.pendingTeleports.containsKey(event.getPlayer().getUniqueId()) && event.getTo() != null) {
         if (event.hasChangedBlock()) {
            this.cancelPending(event.getPlayer(), true);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.cancelPending(event.getPlayer(), false);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPlayedBefore()) {
         Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
               Location location = this.firstJoinLocation(player);
               if (location != null) {
                  player.teleport(location);
               }
            }
         }, 1L);
         if (this.config.getBoolean("prompt-on-join", true) && !this.hasSelection(player.getUniqueId())) {
            long i = Math.max(1L, this.config.getLong("prompt-delay-ticks", 40L));
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline() && !this.hasSelection(player.getUniqueId())) {
                  this.openSelector(player);
                  this.send(player, "prompt-select", new String[0]);
               }
            }, i);
         }
      }
   }

   private Location firstJoinLocation(Player player) {
      Location location = this.mainSpawn();
      if (location != null) {
         return location;
      } else {
         String s = this.config.getString("join-world", "spawn");
         World world = s == null ? null : Bukkit.getWorld(s);
         return world != null ? world.getSpawnLocation() : player.getWorld().getSpawnLocation();
      }
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent event) {
      String s = this.plugin.stateStore().getString(event.getPlayer().getUniqueId(), "spawn-selection", "");
      if (s.equalsIgnoreCase("main")) {
         Location location = this.mainSpawn();
         if (location != null) {
            event.setRespawnLocation(location);
         }
      } else {
         if (s.equalsIgnoreCase("vanilla")) {
            World world = this.vanillaWorld();
            if (world != null) {
               event.setRespawnLocation(this.randomVanillaLocation(world));
            }
         }
      }
   }

   private Location randomVanillaLocation(World world) {
      Location location = this.vanillaSpawn();
      int i = location == null ? 0 : location.getBlockX();
      int j = location == null ? 0 : location.getBlockZ();
      int k = Math.max(0, this.config.getInt("vanilla-random-radius", 256));
      int l = i + ThreadLocalRandom.current().nextInt(-k, k + 1);
      int i1 = j + ThreadLocalRandom.current().nextInt(-k, k + 1);
      int j1 = world.getHighestBlockYAt(l, i1);
      Block block = world.getBlockAt(l, j1, i1);
      return !block.isLiquid() && !block.getType().isAir()
         ? new Location(world, (double)l + 0.5, (double)j1 + 1.0, (double)i1 + 0.5)
         : world.getSpawnLocation();
   }

   private boolean handleSetSpawn(CommandSender sender, String[] args) {
      if (!sender.hasPermission("sharded.spawn.admin")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (sender instanceof Player player) {
         if (args.length == 1 && this.isSelection(args[0])) {
            Location location = player.getLocation();
            if (args[0].equalsIgnoreCase("main")) {
               this.writeLocation("main-spawn", location);
               this.saveConfigFile();
               this.send(sender, "main-set", new String[0]);
            } else {
               this.writeLocation("vanilla-spawn", location);
               this.config.set("vanilla-world", location.getWorld().getName());
               this.saveConfigFile();
               this.send(sender, "vanilla-set", new String[]{"%world%", location.getWorld().getName()});
            }

            return true;
         } else {
            this.send(sender, "setspawn-usage", new String[0]);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void writeLocation(String path, Location location) {
      this.config.set(path, null);
      LocationUtil.write(this.config.createSection(path), location);
   }

   private void saveConfigFile() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (IOException ioexception) {
         this.plugin.getLogger().warning("[spawnselect] Could not save config.yml: " + ioexception.getMessage());
      }
   }

   public Location mainSpawn() {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("main-spawn");
      return configurationsection == null ? null : LocationUtil.read(configurationsection);
   }

   private Location vanillaSpawn() {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("vanilla-spawn");
      return configurationsection == null ? null : LocationUtil.read(configurationsection);
   }

   private World vanillaWorld() {
      String s = this.config.getString("vanilla-world", "");
      return s != null && !s.isBlank() ? Bukkit.getWorld(s) : null;
   }

   private boolean hasSelection(UUID uuid) {
      String s = this.plugin.stateStore().getString(uuid, "spawn-selection", "");
      return s != null && !s.isBlank();
   }

   public void openSelector(Player player) {
      if (this.plugin.gui() != null && this.guiFile != null && this.guiFile.exists() && this.plugin.gui().menu("spawnselect") != null) {
         this.plugin.gui().open(player, "spawnselect");
      } else {
         YamlConfiguration yamlconfiguration = this.guiFile != null && this.guiFile.exists()
            ? YamlConfiguration.loadConfiguration(this.guiFile)
            : new YamlConfiguration();
         SpawnSelectModule.SelectorHolder spawnselectmodule$selectorholder = new SpawnSelectModule.SelectorHolder();
         Inventory inventory = Bukkit.createInventory(
            spawnselectmodule$selectorholder, 27, Text.c(yamlconfiguration.getString("menu_title", this.config.getString("gui.title", "&8Spawn Selector")))
         );
         spawnselectmodule$selectorholder.inventory = inventory;
         TrackedInventories.track(inventory, spawnselectmodule$selectorholder);
         int i = this.validSlot(yamlconfiguration.getInt("items.vanilla.slot", 12), 12);
         int j = this.validSlot(yamlconfiguration.getInt("items.main.slot", 14), 14);
         inventory.setItem(i, this.selectorItem(yamlconfiguration, "items.vanilla", Material.PAPER, "&a&lVanilla Spawn"));
         inventory.setItem(j, this.selectorItem(yamlconfiguration, "items.main", Material.PAPER, "&b&lMain Spawn"));
         spawnselectmodule$selectorholder.actions.put(i, "vanilla");
         spawnselectmodule$selectorholder.actions.put(j, "main");
         player.openInventory(inventory);
      }
   }

   private ItemStack selectorItem(YamlConfiguration gui, String path, Material fallback, String fallbackName) {
      Material material = this.material(gui.getString(path + ".material"), fallback);
      String s = gui.getString(path + ".display_name", gui.getString(path + ".name", fallbackName));
      List<String> list = new ArrayList<>(gui.getStringList(path + ".lore"));
      if (list.isEmpty()) {
         list.add("&7Click to select this spawn.");
      }

      return new ItemBuilder(material).name(s).lore(list).build();
   }

   private int validSlot(int configured, int fallback) {
      return configured >= 0 && configured < 27 ? configured : fallback;
   }

   private Material material(String raw, Material fallback) {
      if (raw != null && !raw.isBlank()) {
         Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   private void playSound(Player player, String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            Sound sound = Sound.valueOf(raw.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 1.0F, 1.0F);
         } catch (IllegalArgumentException illegalargumentexception) {
            player.playSound(player.getLocation(), raw, 1.0F, 1.0F);
         }
      }
   }

   @EventHandler
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         SpawnSelectModule.SelectorHolder spawnselectmodule$selectorholder = TrackedInventories.lookup(
            event.getView().getTopInventory(), SpawnSelectModule.SelectorHolder.class
         );
         if (spawnselectmodule$selectorholder != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               String s = spawnselectmodule$selectorholder.actions.get(event.getSlot());
               if (s != null) {
                  player.closeInventory();
                  this.select(player, s);
               }
            }
         }
      }
   }

   @EventHandler
   public void onInventoryClose(InventoryCloseEvent event) {
      TrackedInventories.untrack(event.getView().getTopInventory(), SpawnSelectModule.SelectorHolder.class);
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (command.getName().equalsIgnoreCase("spawn")) {
         if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "select", "main", "vanilla");
         }

         if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            return TabCompleteHelper.filter(args[1], "main", "vanilla");
         }
      }

      return command.getName().equalsIgnoreCase("setspawn") && args.length == 1 ? TabCompleteHelper.filter(args[0], "main", "vanilla") : List.of();
   }

   private static final class SelectorHolder implements InventoryHolder {
      private final Map<Integer, String> actions = new HashMap<>();
      private Inventory inventory;

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}

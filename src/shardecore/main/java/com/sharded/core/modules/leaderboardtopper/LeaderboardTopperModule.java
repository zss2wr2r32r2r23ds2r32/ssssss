package com.sharded.core.modules.leaderboardtopper;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.leaderboards.LeaderboardsModule;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.ItemDisplay.ItemDisplayTransform;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class LeaderboardTopperModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<String, UUID> displays = new HashMap<>();
   private final Map<String, List<UUID>> lineDisplays = new HashMap<>();
   private final Map<String, List<UUID>> headDisplays = new HashMap<>();
   private NamespacedKey topperKey;
   private NamespacedKey headKey;
   private BukkitTask refreshTask;
   private YamlConfiguration toppers;

   public LeaderboardTopperModule(ShardedCore plugin) {
      super(plugin, "leaderboardtopper");
   }

   @Override
   protected void onEnable() {
      this.topperKey = new NamespacedKey(this.plugin, "lb_topper");
      this.headKey = new NamespacedKey(this.plugin, "lb_topper_head");
      this.loadToppers();
      this.registerCommand("lbplace", this);
      this.registerCommand("lbtopper", this);
      this.registerCommand("lbremove", this);
      this.registerCommand("setline", this);
      this.registerCommand("lbrotate", this);
      this.registerPlaceholders();
      long i = Math.max(20L, this.config.getLong("refresh-seconds", 30L) * 20L);
      this.refreshTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, this::refreshAll, 40L, i);
      this.spawnConfigured();
   }

   @Override
   protected void onDisable() {
      if (this.refreshTask != null) {
         this.refreshTask.cancel();
         this.refreshTask = null;
      }

      for (UUID uuid : new ArrayList<>(this.displays.values())) {
         Entity entity = Bukkit.getEntity(uuid);
         if (entity != null) {
            entity.remove();
         }
      }

      this.displays.clear();

      for (List<UUID> list : new ArrayList<>(this.lineDisplays.values())) {
         for (UUID uuid1 : list) {
            Entity entity1 = Bukkit.getEntity(uuid1);
            if (entity1 != null) {
               entity1.remove();
            }
         }
      }

      this.lineDisplays.clear();

      for (List<UUID> list1 : new ArrayList<>(this.headDisplays.values())) {
         for (UUID uuid2 : list1) {
            Entity entity2 = Bukkit.getEntity(uuid2);
            if (entity2 != null) {
               entity2.remove();
            }
         }
      }

      this.headDisplays.clear();
   }

   private void loadToppers() {
      File file1 = this.leaderboardsToppersFile();
      if (!file1.exists()) {
         this.syncLeaderboardsToppersDefault();
      }

      this.toppers = YamlConfiguration.loadConfiguration(file1);
   }

   private File leaderboardsToppersFile() {
      return new File(this.plugin.getDataFolder(), "modules/leaderboards/toppers.yml");
   }

   private void syncLeaderboardsToppersDefault() {
      File file1 = this.leaderboardsToppersFile();
      if (!file1.getParentFile().exists()) {
         file1.getParentFile().mkdirs();
      }

      if (this.plugin.getResource("modules/leaderboards/toppers.yml") != null) {
         ConfigSync.sync(this.plugin, file1, "modules/leaderboards/toppers.yml");
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!this.isLbAdmin(sender)) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else {
         String s = command.getName().toLowerCase(Locale.ROOT);
         if (s.equals("setline")) {
            return this.cmdSetLine(sender, args);
         } else if (s.equals("lbrotate")) {
            return this.cmdRotate(sender, args);
         } else if (s.equals("lbtopper")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
               this.loadConfigs();
               this.loadToppers();
               this.spawnConfigured();
               this.refreshAll();
               this.send(sender, "reloaded", new String[0]);
               return true;
            } else if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
               this.listPlaceholders(sender);
               return true;
            } else if (args.length >= 2 && sender instanceof Player player1) {
               String s3 = args[0].toLowerCase(Locale.ROOT);
               String s4 = args[1].toLowerCase(Locale.ROOT);
               return this.editBoard(player1, s3, s4, args);
            } else {
               this.send(sender, "usage", new String[0]);
               return true;
            }
         } else if (s.equals("lbremove")) {
            if (args.length == 0) {
               this.send(sender, "usage-remove", new String[0]);
               return true;
            } else {
               String s2 = args[0].toLowerCase(Locale.ROOT);
               if (!s2.equals("near") && !s2.equals("here")) {
                  if (!s2.equals("all") && !s2.equals("world")) {
                     if (!s2.equals("orphans") && !s2.equals("cleanup")) {
                        this.removeBoardFully(s2);
                        this.send(sender, "removed", new String[]{"%board%", s2});
                        return true;
                     } else {
                        int j = this.cleanupOrphans(sender instanceof Player player4 ? player4.getWorld() : null);
                        this.send(sender, "removed-near", new String[]{"%amount%", String.valueOf(j)});
                        return true;
                     }
                  } else if (sender instanceof Player player3) {
                     int k = this.removeInWorld(player3.getWorld());
                     this.send(player3, "removed-near", new String[]{"%amount%", String.valueOf(k)});
                     return true;
                  } else {
                     this.send(sender, "players-only", new String[0]);
                     return true;
                  }
               } else if (sender instanceof Player player2) {
                  double p = 8.0;
                  if (args.length >= 2) {
                     try {
                        p = Double.parseDouble(args[1]);
                     } catch (NumberFormatException numberformatexception) {
                     }
                  }

                  int i = this.removeNearby(player2, p);
                  this.send(player2, "removed-near", new String[]{"%amount%", String.valueOf(i)});
                  return true;
               } else {
                  this.send(sender, "players-only", new String[0]);
                  return true;
               }
            }
         } else if (sender instanceof Player player) {
            if (args.length == 0) {
               this.send(sender, "usage", new String[0]);
               return true;
            } else if (args.length >= 2) {
               return this.editBoard(player, args[0].toLowerCase(Locale.ROOT), args[1].toLowerCase(Locale.ROOT), args);
            } else {
               String s1 = args[0].toLowerCase(Locale.ROOT);
               this.place(player.getLocation(), s1);
               this.saveBoardLocation(s1, player.getLocation());
               this.send(player, "placed", new String[]{"%board%", s1});
               return true;
            }
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      }
   }

   private boolean isLbAdmin(CommandSender sender) {
      return sender.isOp() || sender.hasPermission("sharded.leaderboardtopper.admin");
   }

   private boolean cmdRotate(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         this.send(sender, "players-only", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(sender, "usage-rotate", new String[0]);
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         String s1 = s;
         String s2 = args.length >= 2 ? args[1] : "face";
         String s3 = args.length >= 3 ? args[2] : null;
         if (!s.equals("near") && !s.equals("here") && !s.equals("closest")) {
            if (this.toppers.getConfigurationSection("boards." + s) == null) {
               this.send(player, "unknown-board", new String[]{"%board%", s});
               return true;
            }
         } else {
            s1 = this.nearestBoard(player);
            if (s1 == null) {
               this.send(player, "none-near", new String[0]);
               return true;
            }
         }

         return this.rotateBoard(player, s1, s2, s3);
      }
   }

   private boolean rotateBoard(Player player, String board, String how, String extra) {
      ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards." + board);
      float f = configurationsection == null
         ? player.getLocation().getYaw()
         : (float)configurationsection.getDouble("yaw", (double)player.getLocation().getYaw());
      String s = how == null ? "face" : how.toLowerCase(Locale.ROOT);
      float f1;
      if (s.equals("face") || s.equals("here") || s.equals("me") || s.isBlank()) {
         f1 = player.getLocation().getYaw();
      } else if (s.equals("left") || s.equals("l")) {
         float f3 = parseYaw(extra, 90.0F);
         f1 = f - f3;
      } else if (!s.equals("right") && !s.equals("r")) {
         if (s.equals("flip") || s.equals("mirror")) {
            boolean flag = configurationsection == null || !configurationsection.getBoolean("flip-x", this.config.getBoolean("flip-x", false));
            this.toppers.set("boards." + board + ".flip-x", flag);
            this.saveToppers();
            Location location = this.boardOrigin(board, player.getLocation());
            if (location != null) {
               this.place(location, board);
            } else {
               this.spawnConfigured();
            }

            this.send(player, "flipped", new String[]{"%board%", board, "%mode%", flag ? "on" : "off"});
            return true;
         }

         if (!s.equals("180") && !s.equals("around")) {
            try {
               f1 = Float.parseFloat(s);
            } catch (NumberFormatException numberformatexception) {
               this.send(player, "usage-rotate", new String[0]);
               return true;
            }
         } else {
            f1 = f + 180.0F;
         }
      } else {
         float f2 = parseYaw(extra, 90.0F);
         f1 = f + f2;
      }

      while (f1 > 180.0F) {
         f1 -= 360.0F;
      }

      while (f1 <= -180.0F) {
         f1 += 360.0F;
      }

      this.toppers.set("boards." + board + ".yaw", f1);
      this.toppers.set("boards." + board + ".pitch", 0.0);
      this.saveToppers();
      Location location1 = this.boardOrigin(board, player.getLocation());
      if (location1 != null) {
         location1.setYaw(f1);
         location1.setPitch(0.0F);
         this.place(location1, board);
      } else {
         this.spawnConfigured();
      }

      this.send(player, "rotated", new String[]{"%board%", board, "%yaw%", String.format(Locale.ROOT, "%.0f", f1)});
      return true;
   }

   private static float parseYaw(String raw, float fallback) {
      if (raw != null && !raw.isBlank()) {
         try {
            return Float.parseFloat(raw);
         } catch (NumberFormatException numberformatexception) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private String nearestBoard(Player player) {
      Location location = player.getLocation();
      String s = null;
      double d0 = 144.0;

      for (String s1 : new ArrayList<>(this.displays.keySet())) {
         UUID uuid = this.displays.get(s1);
         Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
         if (entity != null && entity.getWorld() != null && entity.getWorld().equals(location.getWorld())) {
            double d1 = entity.getLocation().distanceSquared(location);
            if (d1 <= d0) {
               d0 = d1;
               s = s1;
            }
         }
      }

      if (s != null) {
         return s;
      } else {
         ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards");
         if (configurationsection == null) {
            return null;
         } else {
            for (String s2 : configurationsection.getKeys(false)) {
               Location location1 = this.boardOrigin(s2, null);
               if (location1 != null && location1.getWorld() != null && location1.getWorld().equals(location.getWorld())) {
                  double d2 = location1.distanceSquared(location);
                  if (d2 <= d0) {
                     d0 = d2;
                     s = s2;
                  }
               }
            }

            return s;
         }
      }
   }

   private boolean cmdSetLine(CommandSender sender, String[] args) {
      this.send(sender, "usage-setline", new String[]{});
      return true;
   }

   private void showLines(CommandSender sender, String board, ConfigurationSection section) {
      this.send(sender, "lines-header", new String[]{"%board%", board});
      List<String> list = section.getStringList("header");
      if (list.isEmpty()) {
         sender.sendMessage(Text.c("&8  header: &7(none)  &8/setline " + board + " addheader &c&lTOP KILLS"));
      } else {
         for (int i = 0; i < list.size(); i++) {
            String s = list.get(i);
            sender.sendMessage(Text.c("&8  H" + (i + 1) + "&7: " + (s != null && !s.isBlank() ? s : "&8(blank)")));
         }
      }

      String s2 = section.getString("line", this.config.getString("line-template", "&c#%rank% &f%name% &f| &c%value%"));
      sender.sendMessage(Text.c("&8  rank &7x" + section.getInt("entries", 10) + "&7: &f" + s2));
      List<String> list1 = section.getStringList("footer");
      if (list1.isEmpty()) {
         sender.sendMessage(Text.c("&8  footer: &7(none)  &8/setline " + board + " addfooter &7Your stats here"));
      } else {
         for (int j = 0; j < list1.size(); j++) {
            String s1 = list1.get(j);
            sender.sendMessage(Text.c("&8  F" + (j + 1) + "&7: " + (s1 != null && !s1.isBlank() ? s1 : "&8(blank)")));
         }
      }

      sender.sendMessage(Text.c("&8  Placeholders: &f%rank% %name% %value%  &8Colors: &f&c &a &#FF0000  &8Blank: &fblank"));
   }

   private boolean setRankFormat(CommandSender sender, String board, String[] args, int from) {
      if (args.length <= from) {
         this.send(sender, "usage-setline-rank", new String[0]);
         return true;
      } else {
         String s = this.joinLine(args, from);
         this.toppers.set("boards." + board + ".line", s);
         this.saveToppers();
         this.refreshAll();
         this.send(sender, "line-set", new String[]{"%board%", board});
         return true;
      }
   }

   private boolean editIndexedList(CommandSender sender, String board, String key, String[] args, boolean indexIsArg1) {
      int i = indexIsArg1 ? 1 : 2;
      int j = i + 1;
      if (args.length <= i) {
         this.send(sender, key.equals("footer") ? "usage-setline-footer" : "usage-setline-header", new String[0]);
         return true;
      } else {
         int k;
         try {
            k = Integer.parseInt(args[i]);
         } catch (NumberFormatException numberformatexception) {
            this.send(sender, key.equals("footer") ? "usage-setline-footer" : "usage-setline-header", new String[0]);
            return true;
         }

         if (k >= 1 && k <= 20) {
            String s = args.length > j ? this.joinLine(args, j) : " ";
            List<String> list = new ArrayList<>(this.toppers.getStringList("boards." + board + "." + key));

            while (list.size() < k) {
               list.add(" ");
            }

            list.set(k - 1, s);
            this.toppers.set("boards." + board + "." + key, list);
            this.saveToppers();
            this.refreshAll();
            this.send(sender, "line-set", new String[]{"%board%", board});
            return true;
         } else {
            this.send(sender, "usage-setline-header", new String[0]);
            return true;
         }
      }
   }

   private boolean appendListLine(CommandSender sender, String board, String key, String[] args, int from) {
      String s = args.length > from ? this.joinLine(args, from) : " ";
      List<String> list = new ArrayList<>(this.toppers.getStringList("boards." + board + "." + key));
      list.add(s);
      this.toppers.set("boards." + board + "." + key, list);
      this.saveToppers();
      this.refreshAll();
      this.send(sender, "line-added", new String[]{"%board%", board});
      return true;
   }

   private boolean insertListLine(CommandSender sender, String board, String key, String[] args) {
      if (args.length < 3) {
         this.send(sender, key.equals("footer") ? "usage-setline-footer" : "usage-setline-header", new String[0]);
         return true;
      } else {
         int i;
         try {
            i = Integer.parseInt(args[2]);
         } catch (NumberFormatException numberformatexception) {
            this.send(sender, "usage-setline-header", new String[0]);
            return true;
         }

         if (i < 1) {
            i = 1;
         }

         String s = args.length > 3 ? this.joinLine(args, 3) : " ";
         List<String> list = new ArrayList<>(this.toppers.getStringList("boards." + board + "." + key));
         if (i <= list.size() + 1) {
            list.add(i - 1, s);
         } else {
            while (list.size() < i - 1) {
               list.add(" ");
            }

            list.add(s);
         }

         this.toppers.set("boards." + board + "." + key, list);
         this.saveToppers();
         this.refreshAll();
         this.send(sender, "line-added", new String[]{"%board%", board});
         return true;
      }
   }

   private boolean removeListLine(CommandSender sender, String board, String key, String[] args) {
      if (args.length < 3) {
         this.send(sender, key.equals("footer") ? "usage-setline-footer" : "usage-setline-header", new String[0]);
         return true;
      } else {
         int i;
         try {
            i = Integer.parseInt(args[2]);
         } catch (NumberFormatException numberformatexception) {
            this.send(sender, "usage-setline-header", new String[0]);
            return true;
         }

         List<String> list = new ArrayList<>(this.toppers.getStringList("boards." + board + "." + key));
         if (i >= 1 && i <= list.size()) {
            list.remove(i - 1);
            this.toppers.set("boards." + board + "." + key, list);
            this.saveToppers();
            this.refreshAll();
            this.send(sender, "line-removed", new String[]{"%board%", board});
            return true;
         } else {
            this.send(sender, "unknown-line", new String[]{"%board%", board});
            return true;
         }
      }
   }

   private boolean clearList(CommandSender sender, String board, String key) {
      this.toppers.set("boards." + board + "." + key, List.of());
      this.saveToppers();
      this.refreshAll();
      this.send(sender, "lines-cleared", new String[]{"%board%", board});
      return true;
   }

   private String joinLine(String[] args, int from) {
      if (from >= args.length) {
         return " ";
      } else {
         String s = String.join(" ", Arrays.copyOfRange(args, from, args.length));
         return !s.equalsIgnoreCase("blank") && !s.equalsIgnoreCase("spacer") && !s.equalsIgnoreCase("empty") ? unescapeUnicode(s) : " ";
      }
   }

   private static String unescapeUnicode(String text) {
      if (text != null && text.contains("\\u")) {
         StringBuilder stringbuilder = new StringBuilder(text.length());

         for (int i = 0; i < text.length(); i++) {
            if (i + 5 < text.length() && text.charAt(i) == '\\' && (text.charAt(i + 1) == 'u' || text.charAt(i + 1) == 'U')) {
               try {
                  stringbuilder.append((char)Integer.parseInt(text.substring(i + 2, i + 6), 16));
                  i += 5;
                  continue;
               } catch (NumberFormatException numberformatexception) {
               }
            }

            stringbuilder.append(text.charAt(i));
         }

         return stringbuilder.toString();
      } else {
         return text;
      }
   }

   private void removeBoardFully(String board) {
      this.remove(board);
      this.toppers.set("boards." + board + ".world", null);
      this.toppers.set("boards." + board + ".x", null);
      this.toppers.set("boards." + board + ".y", null);
      this.toppers.set("boards." + board + ".z", null);
      this.toppers.set("boards." + board + ".yaw", null);
      this.toppers.set("boards." + board + ".pitch", null);
      this.saveToppers();
   }

   private int removeNearby(Player player, double radius) {
      double d0 = radius * radius;
      Location location = player.getLocation();
      int i = 0;

      for (String s : new ArrayList<>(this.displays.keySet())) {
         UUID uuid = this.displays.get(s);
         Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
         if (entity != null
            && entity.getWorld() != null
            && entity.getWorld().equals(location.getWorld())
            && entity.getLocation().distanceSquared(location) <= d0) {
            this.removeBoardFully(s);
            i++;
         }
      }

      return i + this.cleanupOrphansNear(location, radius);
   }

   private int removeInWorld(World world) {
      int i = 0;

      for (String s : new ArrayList<>(this.displays.keySet())) {
         UUID uuid = this.displays.get(s);
         Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
         if (entity != null && world.equals(entity.getWorld())) {
            this.removeBoardFully(s);
            i++;
         }
      }

      return i + this.cleanupOrphans(world);
   }

   private int cleanupOrphans(World world) {
      int i = 0;

      for (World worldx : world == null ? Bukkit.getWorlds() : List.of(world)) {
         for (TextDisplay textdisplay : worldx.getEntitiesByClass(TextDisplay.class)) {
            if (textdisplay.getPersistentDataContainer().has(this.topperKey, PersistentDataType.STRING)) {
               String s = (String)textdisplay.getPersistentDataContainer().get(this.topperKey, PersistentDataType.STRING);
               if (s != null) {
                  String s1 = s.toLowerCase(Locale.ROOT);
                  this.displays.remove(s1);
                  this.lineDisplays.remove(s1);
                  this.headDisplays.remove(s1);
               }

               textdisplay.remove();
               i++;
            }
         }

         for (ItemDisplay itemdisplay : worldx.getEntitiesByClass(ItemDisplay.class)) {
            if (itemdisplay.getPersistentDataContainer().has(this.headKey, PersistentDataType.STRING)) {
               itemdisplay.remove();
               i++;
            }
         }
      }

      return i;
   }

   private int cleanupOrphansNear(Location origin, double radius) {
      double d0 = radius * radius;
      int i = 0;
      World world = origin.getWorld();
      if (world == null) {
         return 0;
      } else {
         for (TextDisplay textdisplay : world.getEntitiesByClass(TextDisplay.class)) {
            if (textdisplay.getPersistentDataContainer().has(this.topperKey, PersistentDataType.STRING)
               && !(textdisplay.getLocation().distanceSquared(origin) > d0)) {
               String s = (String)textdisplay.getPersistentDataContainer().get(this.topperKey, PersistentDataType.STRING);
               if (s != null) {
                  String s1 = s.toLowerCase(Locale.ROOT);
                  this.displays.remove(s1);
                  this.lineDisplays.remove(s1);
                  this.removeHeadsOnly(s1);
               }

               textdisplay.remove();
               i++;
            }
         }

         for (ItemDisplay itemdisplay : world.getEntitiesByClass(ItemDisplay.class)) {
            if (itemdisplay.getPersistentDataContainer().has(this.headKey, PersistentDataType.STRING)
               && itemdisplay.getLocation().distanceSquared(origin) <= d0) {
               itemdisplay.remove();
               i++;
            }
         }

         return i;
      }
   }

   private boolean editBoard(Player player, String board, String action, String[] args) {
      ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards." + board);
      if (configurationsection == null && !action.equals("movehere")) {
         this.send(player, "unknown-board", new String[]{"%board%", board});
         return true;
      } else {
         switch (action) {
            case "movehere":
               this.place(player.getLocation(), board);
               this.saveBoardLocation(board, player.getLocation());
               this.send(player, "moved", new String[]{"%board%", board});
               break;
            case "billboard":
               if (args.length < 3) {
                  this.send(player, "usage-billboard", new String[0]);
                  return true;
               }

               String s2 = args[2].toUpperCase(Locale.ROOT);
               this.toppers.set("boards." + board + ".billboard", s2);
               this.saveToppers();
               this.spawnConfigured();
               this.send(player, "billboard-set", new String[]{"%board%", board, "%mode%", s2});
               break;
            case "viewrange":
            case "visibility":
               if (args.length < 3) {
                  this.send(player, "usage-viewrange", new String[0]);
                  return true;
               }

               int j = Integer.parseInt(args[2]);
               this.toppers.set("boards." + board + ".view-range", j);
               this.saveToppers();
               this.spawnConfigured();
               this.send(player, "viewrange-set", new String[]{"%board%", board, "%range%", String.valueOf(j)});
               break;
            case "rotate":
            case "yaw":
            case "turn":
               return this.rotateBoard(player, board, args.length >= 3 ? args[2] : "face", args.length >= 4 ? args[3] : null);
            case "entries":
               if (args.length < 3) {
                  this.send(player, "usage-entries", new String[0]);
                  return true;
               }

               int i = Math.max(1, Math.min(20, Integer.parseInt(args[2])));
               this.toppers.set("boards." + board + ".entries", i);
               this.saveToppers();
               this.refreshAll();
               this.send(player, "entries-set", new String[]{"%board%", board, "%entries%", String.valueOf(i)});
               break;
            case "line":
               if (args.length < 3) {
                  this.send(player, "usage-line", new String[0]);
                  return true;
               }

               String s1 = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
               this.toppers.set("boards." + board + ".line", s1);
               this.saveToppers();
               this.refreshAll();
               this.send(player, "line-set", new String[]{"%board%", board});
               break;
            case "addline":
            case "header":
               if (args.length < 3) {
                  this.send(player, "usage-addline", new String[0]);
                  return true;
               }

               String s = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
               List<String> list = new ArrayList<>(this.toppers.getStringList("boards." + board + ".header"));
               list.add(s);
               this.toppers.set("boards." + board + ".header", list);
               this.saveToppers();
               this.refreshAll();
               this.send(player, "line-added", new String[]{"%board%", board});
               break;
            default:
               this.send(player, "usage", new String[0]);
         }

         return true;
      }
   }

   private void saveBoardLocation(String board, Location loc) {
      this.toppers.set("boards." + board + ".world", loc.getWorld().getName());
      this.toppers.set("boards." + board + ".x", loc.getX());
      this.toppers.set("boards." + board + ".y", loc.getY());
      this.toppers.set("boards." + board + ".z", loc.getZ());
      this.toppers.set("boards." + board + ".yaw", loc.getYaw());
      this.saveToppers();
   }

   private void saveToppers() {
      try {
         this.toppers.save(this.leaderboardsToppersFile());
      } catch (Exception exception) {
      }
   }

   private void listPlaceholders(CommandSender sender) {
      sender.sendMessage(Text.c("&#5C94FC&lTOPPERS &8▷ &fStored leaderboard placeholder types:"));
      ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards");
      if (configurationsection == null) {
         sender.sendMessage(Text.c("&7No boards configured in modules/leaderboards/toppers.yml"));
      } else {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            String s1 = configurationsection1 == null ? s : configurationsection1.getString("type", s);
            int i = configurationsection1 == null ? 10 : configurationsection1.getInt("entries", 10);
            sender.sendMessage(Text.c("&8• &#5C94FC" + s + " &7(" + s1 + ", top " + i + ")"));
            if (configurationsection1 != null) {
               ConfigurationSection configurationsection2 = configurationsection1.getConfigurationSection("placeholders");
               if (configurationsection2 != null) {
                  for (String s2 : configurationsection2.getKeys(false)) {
                     sender.sendMessage(Text.c("  &7" + s2 + ": &f" + configurationsection2.getString(s2)));
                  }
               }
            }

            for (int j = 1; j <= Math.min(3, i); j++) {
               sender.sendMessage(Text.c("  &f%shardedcore_topper_" + s + "_" + j + "_name%"));
               sender.sendMessage(Text.c("  &f%shardedcore_topper_" + s + "_" + j + "_value%"));
            }
         }

         List<String> list = this.toppers.getStringList("placeholders");
         if (!list.isEmpty()) {
            sender.sendMessage(Text.c("&#5C94FC&lTOPPERS &8▷ &fRoot placeholder index:"));

            for (String s3 : list) {
               sender.sendMessage(Text.c("  &f" + s3));
            }
         }
      }
   }

   private void spawnConfigured() {
      ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null && configurationsection1.contains("world")) {
               World world = Bukkit.getWorld(configurationsection1.getString("world", "world"));
               if (world != null) {
                  Location location = new Location(
                     world, configurationsection1.getDouble("x"), configurationsection1.getDouble("y"), configurationsection1.getDouble("z")
                  );
                  this.place(location, s.toLowerCase(Locale.ROOT));
               }
            }
         }
      }
   }

   private void place(Location loc, String board) {
      this.remove(board);
      if (loc.getWorld() != null) {
         ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards." + board);
         Billboard billboard = this.parseBillboard(
            configurationsection != null
               ? configurationsection.getString("billboard", this.config.getString("billboard", "FIXED"))
               : this.config.getString("billboard", "FIXED")
         );
         int i = configurationsection != null
            ? configurationsection.getInt("view-range", this.config.getInt("view-range", 48))
            : this.config.getInt("view-range", 48);
         int j = configurationsection != null
            ? configurationsection.getInt("line-width", this.config.getInt("line-width", 220))
            : this.config.getInt("line-width", 220);
         float f = configurationsection != null ? (float)configurationsection.getDouble("yaw", (double)loc.getYaw()) : loc.getYaw();
         float f1 = configurationsection != null ? (float)configurationsection.getDouble("pitch", 0.0) : 0.0F;
         float f2 = (float)this.config.getDouble("scale", 1.0);
         double d0 = this.lineHeight(configurationsection);
         List<String> list = this.buildLineStrings(board);
         int k = configurationsection == null ? 0 : configurationsection.getStringList("header").size();
         List<LeaderboardTopperModule.TopperEntry> list1 = this.fetchEntries(
            this.plugin.modules().get(LeaderboardsModule.class), configurationsection == null ? board : configurationsection.getString("type", board)
         );
         List<UUID> list2 = new ArrayList<>();
         List<UUID> list3 = new ArrayList<>();
         double d1 = Math.abs(
            configurationsection != null
               ? configurationsection.getDouble("text-gap", this.config.getDouble("text-gap", 0.22))
               : this.config.getDouble("text-gap", 0.22)
         );
         boolean flag = configurationsection != null
            ? configurationsection.getBoolean("flip-x", this.config.getBoolean("flip-x", false))
            : this.config.getBoolean("flip-x", false);
         double d2 = flag ? -1.0 : 1.0;
         double d3 = configurationsection != null
            ? configurationsection.getDouble("head-x-offset", this.config.getDouble("head-x-offset", 0.0))
            : this.config.getDouble("head-x-offset", 0.0);
         double d4 = configurationsection != null
            ? configurationsection.getDouble("head-y-start", this.config.getDouble("head-y-start", 0.08))
            : this.config.getDouble("head-y-start", 0.08);
         float f3 = (float)(
            configurationsection != null
               ? configurationsection.getDouble("head-scale", this.config.getDouble("head-scale", 0.36))
               : this.config.getDouble("head-scale", 0.36)
         );
         float f4 = (float)(
            configurationsection != null
               ? configurationsection.getDouble("head-flatness", this.config.getDouble("head-flatness", 0.03))
               : this.config.getDouble("head-flatness", 0.03)
         );
         int l = Math.min(
            configurationsection == null ? 10 : Math.max(1, Math.min(20, configurationsection.getInt("entries", 10))),
            this.config.getInt("heads-per-board", 10)
         );
         boolean flag1 = this.config.getBoolean("player-heads", true);
         double d5 = 0.025 * (double)f2;
         int i1 = k + (configurationsection == null ? 10 : Math.max(1, Math.min(20, configurationsection.getInt("entries", 10))));
         int j1 = 0;

         for (int k1 = k; k1 < Math.min(i1, list.size()); k1++) {
            j1 = Math.max(j1, textPixelWidth(list.get(k1)));
         }

         for (int j2 = 0; j2 < list.size(); j2++) {
            Location location = loc.clone().add(0.0, (double)(-j2) * d0, 0.0);
            String s = list.get(j2);
            boolean flag2 = j2 >= k && j2 < i1;
            int l1 = flag2 ? textPixelWidth(s) : j1;
            double d6 = d1 + (double)l1 * d5 / 2.0;
            Location location1 = location.clone();
            this.applyLocalOffset(location1, f, d2 * d6, 0.0);
            TextDisplay textdisplay = (TextDisplay)location1.getWorld().spawn(location1, TextDisplay.class, entity -> {
               entity.setPersistent(true);
               entity.setInvulnerable(true);
               entity.setGravity(false);
               entity.setBillboard(billboard);
               entity.setAlignment(TextAlignment.LEFT);
               entity.setShadowed(true);
               entity.setSeeThrough(false);
               entity.setDefaultBackground(false);
               entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
               entity.setLineWidth(j);
               entity.setViewRange((float)i);
               entity.setRotation(f, f1);
               entity.setTransformation(new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(f2, f2, f2), new Quaternionf()));
               entity.getPersistentDataContainer().set(this.topperKey, PersistentDataType.STRING, board);
               entity.text(Text.c(s));
            });
            list2.add(textdisplay.getUniqueId());
            int i2 = j2 - k;
            if (flag1 && flag2 && i2 < l && i2 < list1.size()) {
               Location location2 = location.clone().add(0.0, d4, 0.0);
               this.applyLocalOffset(location2, f, -d2 * d3, 0.02);
               float f5 = Math.max(0.01F, f4);
               ItemDisplay itemdisplay = (ItemDisplay)location2.getWorld().spawn(location2, ItemDisplay.class, entity -> {
                  entity.setPersistent(true);
                  entity.setInvulnerable(true);
                  entity.setGravity(false);
                  entity.setBillboard(billboard);
                  entity.setItemDisplayTransform(ItemDisplayTransform.FIXED);
                  entity.setViewRange((float)i);
                  entity.setRotation(f, f1);
                  ItemStack itemstack = new ItemStack(Material.PLAYER_HEAD);
                  LeaderboardTopperModule.TopperEntry leaderboardtoppermodule$topperentry = list1.get(i2);
                  if (itemstack.getItemMeta() instanceof SkullMeta skullmeta) {
                     if (leaderboardtoppermodule$topperentry.uuid() != null) {
                        skullmeta.setOwningPlayer(Bukkit.getOfflinePlayer(leaderboardtoppermodule$topperentry.uuid()));
                     } else if (leaderboardtoppermodule$topperentry.name() != null && !leaderboardtoppermodule$topperentry.name().isBlank()) {
                        skullmeta.setOwningPlayer(Bukkit.getOfflinePlayer(leaderboardtoppermodule$topperentry.name()));
                     }

                     itemstack.setItemMeta(skullmeta);
                  }

                  entity.setItemStack(itemstack);
                  entity.setTransformation(new Transformation(new Vector3f(0.0F, 0.0F, 0.0F), new Quaternionf(), new Vector3f(f3, f3, f5), new Quaternionf()));
                  entity.getPersistentDataContainer().set(this.headKey, PersistentDataType.STRING, board);
               });
               list3.add(itemdisplay.getUniqueId());
            }
         }

         if (!list2.isEmpty()) {
            this.displays.put(board, list2.get(0));
         }

         this.lineDisplays.put(board, list2);
         this.headDisplays.put(board, list3);
      }
   }

   private List<String> buildLineStrings(String board) {
      ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards." + board);
      String s = configurationsection == null ? board : configurationsection.getString("type", board);
      int i = configurationsection == null ? 10 : Math.max(1, Math.min(20, configurationsection.getInt("entries", 10)));
      String s1 = configurationsection == null
         ? this.config.getString("line-template", "&c#%rank% &f%name% &f| &c%value%")
         : configurationsection.getString("line", this.config.getString("line-template", "&c#%rank% &f%name% &f| &c%value%"));
      s1 = s1.replace("%head%", "");
      List<String> list = configurationsection == null ? List.of() : configurationsection.getStringList("header");
      List<String> list1 = configurationsection == null ? List.of() : configurationsection.getStringList("footer");
      String s2 = configurationsection == null
         ? this.config.getString("empty-name", "---")
         : configurationsection.getString("empty-name", this.config.getString("empty-name", "---"));
      String s3 = configurationsection == null
         ? this.config.getString("empty-value", "---")
         : configurationsection.getString("empty-value", this.config.getString("empty-value", "---"));
      List<LeaderboardTopperModule.TopperEntry> list2 = this.fetchEntries(this.plugin.modules().get(LeaderboardsModule.class), s);
      List<String> list3 = new ArrayList<>();

      for (String s4 : list) {
         list3.add(s4.replace("%head%", ""));
      }

      for (int j = 0; j < i; j++) {
         String s6 = String.format(Locale.ROOT, "%-2d", j + 1);
         if (j < list2.size()) {
            LeaderboardTopperModule.TopperEntry leaderboardtoppermodule$topperentry = list2.get(j);
            String s5 = this.displayName(leaderboardtoppermodule$topperentry.name(), s2);
            list3.add(Text.apply(s1, "%rank%", s6, "%name%", s5, "%value%", this.formatValue(s, leaderboardtoppermodule$topperentry.value())));
         } else {
            list3.add(Text.apply(s1, "%rank%", s6, "%name%", s2, "%value%", s3));
         }
      }

      list3.addAll(list1);
      return list3;
   }

   private double lineHeight(ConfigurationSection section) {
      double d0 = section != null ? section.getDouble("head-y-step", this.config.getDouble("head-y-step", -0.28)) : this.config.getDouble("head-y-step", -0.28);
      return Math.max(0.18, Math.abs(d0));
   }

   private Billboard parseBillboard(String raw) {
      if (raw == null) {
         return Billboard.VERTICAL;
      } else {
         String s = raw.trim().toUpperCase(Locale.ROOT);

         return switch (s) {
            case "FIXED" -> Billboard.FIXED;
            case "HORIZONTAL" -> Billboard.HORIZONTAL;
            case "CENTER", "CENTRE" -> Billboard.CENTER;
            default -> Billboard.VERTICAL;
         };
      }
   }

   private TextAlignment parseAlignment(String raw) {
      if (raw == null) {
         return TextAlignment.LEFT;
      } else {
         String s = raw.trim().toUpperCase(Locale.ROOT);

         return switch (s) {
            case "CENTER", "CENTRE" -> TextAlignment.CENTER;
            case "RIGHT" -> TextAlignment.RIGHT;
            default -> TextAlignment.LEFT;
         };
      }
   }

   private Location boardOrigin(String board, Location fallback) {
      ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards." + board);
      if (configurationsection != null && configurationsection.contains("world") && configurationsection.contains("x")) {
         World world = Bukkit.getWorld(configurationsection.getString("world", ""));
         if (world == null) {
            return fallback == null ? null : fallback.clone();
         } else {
            float f = fallback != null ? fallback.getYaw() : (float)configurationsection.getDouble("yaw", 0.0);
            return new Location(
               world,
               configurationsection.getDouble("x"),
               configurationsection.getDouble("y"),
               configurationsection.getDouble("z"),
               (float)configurationsection.getDouble("yaw", (double)f),
               (float)configurationsection.getDouble("pitch", 0.0)
            );
         }
      } else {
         return fallback == null ? null : fallback.clone();
      }
   }

   static int textPixelWidth(String text) {
      if (text != null && !text.isEmpty()) {
         int i = 0;
         boolean flag = false;
         int j = 0;

         while (j < text.length()) {
            char c0 = text.charAt(j);
            if ((c0 == '&' || c0 == 167) && j + 1 < text.length()) {
               char c1 = Character.toLowerCase(text.charAt(j + 1));
               if (c1 == '#' && j + 7 < text.length()) {
                  flag = false;
                  j += 8;
                  continue;
               }

               if (c1 == 'x' && j + 13 < text.length()) {
                  flag = false;
                  j += 14;
                  continue;
               }

               if (c1 >= '0' && c1 <= '9' || c1 >= 'a' && c1 <= 'f') {
                  flag = false;
                  j += 2;
                  continue;
               }

               if (c1 == 'l') {
                  flag = true;
                  j += 2;
                  continue;
               }

               if (c1 == 'r') {
                  flag = false;
                  j += 2;
                  continue;
               }

               if (c1 == 'k' || c1 == 'm' || c1 == 'n' || c1 == 'o') {
                  j += 2;
                  continue;
               }
            }

            i += glyphAdvance(c0) + (flag ? 1 : 0);
            j++;
         }

         return i;
      } else {
         return 0;
      }
   }

   private static int glyphAdvance(char ch) {
      return switch (ch) {
         case ' ', 'I', '[', ']', 't' -> 4;
         case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
         case '"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
         default -> 6;
         case '@', '~' -> 7;
         case '`', 'l' -> 3;
      };
   }

   private void applyLocalOffset(Location loc, float yaw, double right, double forward) {
      double d0 = Math.toRadians((double)yaw);
      double d1 = -Math.sin(d0);
      double d2 = Math.cos(d0);
      double d3 = Math.cos(d0);
      double d4 = Math.sin(d0);
      loc.add(d3 * right + d1 * forward, 0.0, d4 * right + d2 * forward);
   }

   private void remove(String board) {
      UUID uuid = this.displays.remove(board);
      if (uuid != null) {
         Entity entity = Bukkit.getEntity(uuid);
         if (entity != null) {
            entity.remove();
         }
      }

      List<UUID> list = this.lineDisplays.remove(board);
      if (list != null) {
         for (UUID uuid1 : list) {
            Entity entity1 = Bukkit.getEntity(uuid1);
            if (entity1 != null) {
               entity1.remove();
            }
         }
      }

      this.removeHeadsOnly(board);
   }

   private void removeHeadsOnly(String board) {
      List<UUID> list = this.headDisplays.remove(board);
      if (list != null) {
         for (UUID uuid : list) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
               entity.remove();
            }
         }
      }
   }

   private void refreshAll() {
      for (String s : new ArrayList<>(this.displays.keySet())) {
         Location location = this.boardOrigin(s, null);
         if (location == null || location.getWorld() == null) {
            UUID uuid = this.displays.get(s);
            Entity entity = uuid == null ? null : Bukkit.getEntity(uuid);
            if (entity == null) {
               continue;
            }

            location = entity.getLocation();
         }

         this.place(location, s);
      }
   }

   private Component render(String board) {
      ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards." + board);
      String s = configurationsection == null ? board : configurationsection.getString("type", board);
      int i = configurationsection == null ? 10 : Math.max(1, Math.min(20, configurationsection.getInt("entries", 10)));
      String s1 = configurationsection == null
         ? this.config.getString("line-template", "&c#%rank% &f%name% &f| &c%value%")
         : configurationsection.getString("line", this.config.getString("line-template", "&c#%rank% &f%name% &f| &c%value%"));
      List<String> list = configurationsection == null ? List.of() : configurationsection.getStringList("header");
      List<String> list1 = configurationsection == null ? List.of() : configurationsection.getStringList("footer");
      String s2 = configurationsection == null
         ? this.config.getString("empty-name", "---")
         : configurationsection.getString("empty-name", this.config.getString("empty-name", "---"));
      String s3 = configurationsection == null
         ? this.config.getString("empty-value", "---")
         : configurationsection.getString("empty-value", this.config.getString("empty-value", "---"));
      LeaderboardsModule leaderboardsmodule = this.plugin.modules().get(LeaderboardsModule.class);
      List<LeaderboardTopperModule.TopperEntry> list2 = this.fetchEntries(leaderboardsmodule, s);
      TextComponent textcomponent = Component.empty();
      boolean flag = true;

      for (String s4 : list) {
         if (!flag) {
            textcomponent = (TextComponent)textcomponent.append(Component.newline());
         }

         textcomponent = (TextComponent)textcomponent.append(Text.c(s4.replace("%head%", "")));
         flag = false;
      }

      for (int j = 0; j < i; j++) {
         if (!flag) {
            textcomponent = (TextComponent)textcomponent.append(Component.newline());
         }

         String s7 = String.format(Locale.ROOT, "%-2d", j + 1);
         if (j < list2.size()) {
            LeaderboardTopperModule.TopperEntry leaderboardtoppermodule$topperentry = list2.get(j);
            String s5 = this.formatValue(s, leaderboardtoppermodule$topperentry.value());
            String s6 = this.displayName(leaderboardtoppermodule$topperentry.name(), s2);
            textcomponent = (TextComponent)textcomponent.append(Text.c(Text.apply(s1.replace("%head%", ""), "%rank%", s7, "%name%", s6, "%value%", s5)));
         } else {
            textcomponent = (TextComponent)textcomponent.append(Text.c(Text.apply(s1.replace("%head%", ""), "%rank%", s7, "%name%", s2, "%value%", s3)));
         }

         flag = false;
      }

      for (String s8 : list1) {
         if (!flag) {
            textcomponent = (TextComponent)textcomponent.append(Component.newline());
         }

         textcomponent = (TextComponent)textcomponent.append(Text.c(s8));
         flag = false;
      }

      return textcomponent;
   }

   private List<LeaderboardTopperModule.TopperEntry> fetchEntries(LeaderboardsModule module, String type) {
      if (module == null) {
         return List.of();
      } else {
         try {
            Object object = module.service();
            if (object == null) {
               return List.of();
            } else {
               Method method = object.getClass().getDeclaredMethod("entries", String.class);
               method.setAccessible(true);
               List<?> list = (List<?>)method.invoke(object, type);
               List<LeaderboardTopperModule.TopperEntry> list1 = new ArrayList<>(list.size());

               for (Object object1 : list) {
                  Method method1 = object1.getClass().getDeclaredMethod("displayName");
                  Method method2 = object1.getClass().getDeclaredMethod("value");
                  method1.setAccessible(true);
                  method2.setAccessible(true);
                  UUID uuid = null;

                  try {
                     Method method3 = object1.getClass().getDeclaredMethod("uuid");
                     method3.setAccessible(true);
                     if (method3.invoke(object1) instanceof UUID uuid2) {
                        uuid = uuid2;
                     }
                  } catch (ReflectiveOperationException reflectiveoperationexception1) {
                     try {
                        Method method4 = object1.getClass().getDeclaredMethod("uniqueId");
                        method4.setAccessible(true);
                        if (method4.invoke(object1) instanceof UUID uuid1) {
                           uuid = uuid1;
                        }
                     } catch (ReflectiveOperationException reflectiveoperationexception) {
                     }
                  }

                  String s = String.valueOf(method1.invoke(object1));
                  if (uuid == null) {
                     OfflinePlayer offlineplayer = Bukkit.getOfflinePlayerIfCached(s);
                     if (offlineplayer != null) {
                        uuid = offlineplayer.getUniqueId();
                     }
                  }

                  list1.add(new LeaderboardTopperModule.TopperEntry(s, ((Number)method2.invoke(object1)).longValue(), uuid));
               }

               return list1;
            }
         } catch (ReflectiveOperationException reflectiveoperationexception2) {
            this.plugin.getLogger().warning("[leaderboardtopper] Could not read leaderboard entries: " + reflectiveoperationexception2.getMessage());
            return List.of();
         }
      }
   }

   private void registerPlaceholders() {
      // PlaceholderAPI expansion for toppers is optional; leaderboardboards owns %leaderboard_*%.
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         this.plugin.getLogger().info("[leaderboardtopper] PlaceholderAPI present (topper expansion skipped; use leaderboard boards).");
      }
   }

   private String resolveTopperPlaceholder(String params) {
      if (params != null && params.startsWith("topper_")) {
         String[] astring = params.split("_");
         if (astring.length < 4) {
            return "";
         } else {
            String s = astring[1];

            int i;
            try {
               i = Integer.parseInt(astring[2]);
            } catch (NumberFormatException numberformatexception) {
               return "";
            }

            String s1 = astring[3];
            ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards." + s);
            String s2 = configurationsection == null ? s : configurationsection.getString("type", s);
            List<LeaderboardTopperModule.TopperEntry> list = this.fetchEntries(this.plugin.modules().get(LeaderboardsModule.class), s2);
            int j = configurationsection == null ? 10 : configurationsection.getInt("entries", 10);
            if (i >= 1 && i <= j) {
               String s3 = configurationsection == null
                  ? this.config.getString("empty-name", "---")
                  : configurationsection.getString("empty-name", this.config.getString("empty-name", "---"));
               String s4 = configurationsection == null
                  ? this.config.getString("empty-value", "---")
                  : configurationsection.getString("empty-value", this.config.getString("empty-value", "---"));
               if (i <= list.size()) {
                  LeaderboardTopperModule.TopperEntry leaderboardtoppermodule$topperentry = list.get(i - 1);
                  if (s1.equalsIgnoreCase("name")) {
                     return this.displayName(leaderboardtoppermodule$topperentry.name(), s3);
                  } else {
                     return s1.equalsIgnoreCase("value") ? this.formatValue(s2, leaderboardtoppermodule$topperentry.value()) : "";
                  }
               } else if (s1.equalsIgnoreCase("name")) {
                  return s3;
               } else {
                  return s1.equalsIgnoreCase("value") ? s4 : "";
               }
            } else {
               return "";
            }
         }
      } else {
         return null;
      }
   }

   private String displayName(String raw, String emptyName) {
      if (raw != null && !raw.isBlank()) {
         StringBuilder stringbuilder = new StringBuilder(raw.length());
         boolean flag = true;

         for (int i = 0; i < raw.length(); i++) {
            char c0 = raw.charAt(i);
            if (c0 == '_' || c0 == '-' || c0 == ' ') {
               stringbuilder.append(c0);
               flag = true;
            } else if (flag) {
               stringbuilder.append(Character.toUpperCase(c0));
               flag = false;
            } else {
               stringbuilder.append(Character.toLowerCase(c0));
            }
         }

         return stringbuilder.toString();
      } else {
         return emptyName;
      }
   }

   private String formatValue(String type, long value) {
      return type.toLowerCase(Locale.ROOT).contains("playtime") ? Text.formatPlaytime(value) : String.valueOf(value);
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!this.isLbAdmin(sender)) {
         return List.of();
      } else {
         String s = command.getName().toLowerCase(Locale.ROOT);
         List<String> list = new ArrayList<>();
         ConfigurationSection configurationsection = this.toppers.getConfigurationSection("boards");
         if (configurationsection != null) {
            list.addAll(configurationsection.getKeys(false));
         } else {
            list.addAll(List.of("tokens", "kills", "deaths", "playtime", "killstreaks"));
         }

         List<String> list1 = List.of("movehere", "billboard", "viewrange", "visibility", "rotate", "entries", "line", "addline", "header");
         if (s.equals("lbrotate")) {
            if (args.length == 1) {
               List<String> list5 = new ArrayList<>(list);
               list5.addAll(List.of("near", "here"));
               return TabCompleteHelper.filter(args[0], list5);
            } else {
               return args.length == 2 ? TabCompleteHelper.filter(args[1], "face", "left", "right", "180", "flip", "0", "90", "-90") : List.of();
            }
         } else if (!s.equals("setline")) {
            if (s.equals("lbtopper")) {
               if (args.length == 1) {
                  List<String> list4 = new ArrayList<>(list);
                  list4.add("reload");
                  list4.add("list");
                  return TabCompleteHelper.filter(args[0], list4);
               } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload") && !args[0].equalsIgnoreCase("list")) {
                  return TabCompleteHelper.filter(args[1], list1);
               } else {
                  return args.length == 3 && args[1].equalsIgnoreCase("billboard")
                     ? TabCompleteHelper.filter(args[2], "FIXED", "VERTICAL", "HORIZONTAL", "CENTER")
                     : List.of();
               }
            } else if (s.equals("lbremove")) {
               if (args.length == 1) {
                  List<String> list3 = new ArrayList<>(list);
                  list3.addAll(List.of("near", "here", "all", "world", "orphans", "cleanup"));
                  return TabCompleteHelper.filter(args[0], list3);
               } else {
                  return List.of();
               }
            } else if (args.length == 1) {
               return TabCompleteHelper.filter(args[0], list);
            } else if (args.length == 2) {
               return TabCompleteHelper.filter(args[1], list1);
            } else {
               return args.length == 3 && args[1].equalsIgnoreCase("billboard")
                  ? TabCompleteHelper.filter(args[2], "FIXED", "VERTICAL", "HORIZONTAL", "CENTER")
                  : List.of();
            }
         } else if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], list);
         } else if (args.length == 2) {
            List<String> list2 = new ArrayList<>(
               List.of(
                  "show",
                  "rank",
                  "header",
                  "footer",
                  "addheader",
                  "addfooter",
                  "insertheader",
                  "insertfooter",
                  "delheader",
                  "delfooter",
                  "clearheader",
                  "clearfooter",
                  "blankheader",
                  "blankfooter",
                  "&c#%rank% &f%name% &f| &c%value%"
               )
            );
            list2.addAll(List.of("1", "2", "3"));
            return TabCompleteHelper.filter(args[1], list2);
         } else {
            return args.length != 3
                  || !args[1].equalsIgnoreCase("header")
                     && !args[1].equalsIgnoreCase("footer")
                     && !args[1].equalsIgnoreCase("h")
                     && !args[1].equalsIgnoreCase("f")
                     && !args[1].equalsIgnoreCase("delheader")
                     && !args[1].equalsIgnoreCase("delfooter")
                     && !args[1].equalsIgnoreCase("insertheader")
                     && !args[1].equalsIgnoreCase("insertfooter")
                     && !args[1].equalsIgnoreCase("delh")
                     && !args[1].equalsIgnoreCase("delf")
               ? List.of()
               : TabCompleteHelper.filter(args[2], "1", "2", "3", "4", "5");
         }
      }
   }

   private static record TopperEntry(String name, long value, UUID uuid) {
   }
}

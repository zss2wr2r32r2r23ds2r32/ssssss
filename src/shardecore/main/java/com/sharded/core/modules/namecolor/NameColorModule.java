package com.sharded.core.modules.namecolor;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticService;
import com.sharded.core.module.Module;
import com.sharded.core.util.ColorConfigUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class NameColorModule extends Module implements CommandExecutor, TabCompleter {
   private static final Pattern GRADIENT_INPUT = Pattern.compile("^#?[0-9A-Fa-f]{6}\\s+#?[0-9A-Fa-f]{6}$");
   private static final String MENU_TITLE = "Name Colours";
   private final Map<String, NameColorModule.ColorOption> colors = new LinkedHashMap<>();
   private final Map<UUID, Boolean> awaitingGradient = new ConcurrentHashMap<>();
   private NameColorDatabase database;

   public NameColorModule(ShardedCore plugin) {
      super(plugin, "namecolor");
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new NameColorDatabase(this.plugin, this.moduleFolder());
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open namecolor database", exception);
      }

      this.loadColors();
      this.registerCommand("namecolor", this);
      this.registerCommand("namecolors", this);
   }

   @Override
   protected void onDisable() {
      this.awaitingGradient.clear();
      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
   }

   private void loadColors() {
      this.colors.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("colors");
      if (configurationsection != null) {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null && configurationsection1.contains("slot")) {
               this.colors
                  .put(
                     s,
                     new NameColorModule.ColorOption(
                        s,
                        configurationsection1.getInt("slot", 0),
                        ColorConfigUtil.resolvePermission(s, configurationsection1, "sharded.namecolor."),
                        ColorConfigUtil.resolveValue(configurationsection1, "&f"),
                        configurationsection1.getString("material", "RED_DYE"),
                        configurationsection1.getString("display-name", s),
                        configurationsection1.getStringList("lore"),
                        configurationsection1.getBoolean("custom-input", false)
                     )
                  );
            }
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         if (sender instanceof Player player1) {
            if (!player1.hasPermission("sharded.namecolor.use")) {
               this.send(player1, "no-permission", new String[0]);
               return true;
            } else {
               this.openMenu(player1);
               return true;
            }
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         if (s.equals("create") && args.length >= 3) {
            if (!sender.hasPermission("sharded.namecolor.admin")) {
               this.send(sender, "no-permission", new String[0]);
               return true;
            } else {
               this.createColor(sender, args[1].toLowerCase(Locale.ROOT), String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
               return true;
            }
         } else if (s.equals("delete") && args.length >= 2) {
            if (!sender.hasPermission("sharded.namecolor.admin")) {
               this.send(sender, "no-permission", new String[0]);
               return true;
            } else {
               this.deleteColor(sender, args[1].toLowerCase(Locale.ROOT));
               return true;
            }
         } else if (s.equals("reset")) {
            if (sender instanceof Player player5) {
               this.resetColor(player5);
               return true;
            } else {
               this.send(sender, "players-only", new String[0]);
               return true;
            }
         } else if (s.equals("set") && args.length >= 2) {
            if (sender instanceof Player player4) {
               this.applyColorById(player4, args[1].toLowerCase(Locale.ROOT));
               return true;
            } else {
               this.send(sender, "players-only", new String[0]);
               return true;
            }
         } else if (s.equals("custom")) {
            if (sender instanceof Player player3) {
               if (!player3.hasPermission("sharded.namecolor.custom")) {
                  this.send(player3, "not-owned", new String[]{"%color%", "Custom"});
                  return true;
               } else {
                  this.awaitingGradient.put(player3.getUniqueId(), true);
                  this.send(player3, "custom-prompt", new String[0]);
                  return true;
               }
            } else {
               this.send(sender, "players-only", new String[0]);
               return true;
            }
         } else if (s.equals("gradient")) {
            if (args.length >= 4 && sender.hasPermission("sharded.namecolor.admin")) {
               this.createColor(sender, args[1].toLowerCase(Locale.ROOT), args[2] + " " + args[3]);
               return true;
            } else if (args.length >= 3 && sender instanceof Player player2) {
               String s1 = this.normalizeGradientInput(args[1] + " " + args[2]);
               if (s1 == null) {
                  this.send(player2, "custom-invalid", new String[0]);
                  return true;
               } else {
                  this.applyGradient(player2, s1, false);
                  return true;
               }
            } else {
               this.send(sender, "gradient-usage", new String[0]);
               return true;
            }
         } else if (sender instanceof Player player) {
            if (!player.hasPermission("sharded.namecolor.use")) {
               this.send(player, "no-permission", new String[0]);
               return true;
            } else {
               this.applyColorById(player, s);
               return true;
            }
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      }
   }

   private void applyColorById(Player player, String id) {
      NameColorModule.ColorOption namecolormodule$coloroption = this.colors.get(id);
      if (namecolormodule$coloroption != null) {
         if (!player.hasPermission(namecolormodule$coloroption.permission())) {
            this.send(player, "not-owned", new String[]{"%color%", namecolormodule$coloroption.displayName()});
         } else if (namecolormodule$coloroption.customInput()) {
            String s2 = this.database == null ? null : this.database.getLastGradient(player.getUniqueId());
            if (s2 != null && !s2.isBlank()) {
               this.applyGradient(player, s2, false);
            } else {
               this.awaitingGradient.put(player.getUniqueId(), true);
               this.send(player, "custom-prompt", new String[0]);
            }
         } else {
            this.applyColorValue(player, namecolormodule$coloroption.value(), namecolormodule$coloroption.displayName());
         }
      } else {
         ConfigurationSection configurationsection = this.config.getConfigurationSection("colors." + id);
         if (configurationsection != null) {
            String s = configurationsection.getString("permission", "sharded.namecolor." + id);
            if (!player.hasPermission(s)) {
               this.send(player, "not-owned", new String[]{"%color%", id});
            } else {
               String s1 = configurationsection.getString("value", "&f");
               this.applyColorValue(player, s1, configurationsection.getString("display-name", id));
            }
         } else {
            this.send(player, "color-not-found", new String[]{"%color%", id});
         }
      }
   }

   private void applyColorValue(Player player, String value, String label) {
      CosmeticService cosmeticservice = this.plugin.cosmetics();
      if (cosmeticservice != null) {
         cosmeticservice.setNameColor(player, CosmeticService.normalizeColorSpec(value));
      }

      this.send(player, "applied", new String[]{"%color%", label});
   }

   private void resetColor(Player player) {
      CosmeticService cosmeticservice = this.plugin.cosmetics();
      if (cosmeticservice != null) {
         cosmeticservice.clearNameColor(player);
      }

      this.send(player, "reset", new String[0]);
   }

   private void createColor(CommandSender sender, String id, String value) {
      String s = CosmeticService.normalizeColorSpec(value);
      if (this.config.getConfigurationSection("colors." + id) != null) {
         this.config.set("colors." + id + ".value", s);
         this.saveConfig();
         this.loadColors();
         this.send(sender, "color-created", new String[]{"%color%", id});
      } else {
         this.config.set("colors." + id + ".permission", "sharded.namecolor." + id);
         this.config.set("colors." + id + ".value", s);
         this.saveConfig();
         this.loadColors();
         this.send(sender, "color-created", new String[]{"%color%", id});
      }
   }

   private void deleteColor(CommandSender sender, String id) {
      if (this.config.getConfigurationSection("colors." + id) == null) {
         this.send(sender, "color-not-found", new String[]{"%color%", id});
      } else {
         this.config.set("colors." + id, null);
         this.saveConfig();
         this.loadColors();
         this.send(sender, "color-deleted", new String[]{"%color%", id});
      }
   }

   private int nextFreeSlot(int size) {
      Set<Integer> set = this.colors.values().stream().map(NameColorModule.ColorOption::slot).collect(Collectors.toSet());
      int i = this.config.getInt("reset.slot", 4);
      set.add(i);

      for (int j = 0; j < size; j++) {
         if (!set.contains(j)) {
            return j;
         }
      }

      return -1;
   }

   private void saveConfig() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
         this.plugin.getLogger().warning("[namecolor] Could not save config: " + exception.getMessage());
      }
   }

   public void openMenu(Player player) {
      int i = this.config.getInt("menu-rows", 6);
      NameColorModule.MenuHolder namecolormodule$menuholder = new NameColorModule.MenuHolder();
      Inventory inventory = this.plugin.getServer().createInventory(namecolormodule$menuholder, i * 9, Text.c("Name Colours"));
      TrackedInventories.track(inventory, namecolormodule$menuholder);
      Material material = Material.matchMaterial(this.config.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
      if (material == null) {
         material = Material.BLACK_STAINED_GLASS_PANE;
      }

      ItemStack itemstack = new ItemBuilder(material).name(" ").hideAll().build();

      for (int j = 0; j < inventory.getSize(); j++) {
         inventory.setItem(j, itemstack.clone());
      }

      Map<String, String> map = this.equipPlaceholders(player);

      for (NameColorModule.ColorOption namecolormodule$coloroption : this.colors.values()) {
         Material material1 = Material.matchMaterial(namecolormodule$coloroption.material());
         if (material1 == null) {
            material1 = Material.RED_DYE;
         }

         inventory.setItem(
            namecolormodule$coloroption.slot(),
            new ItemBuilder(material1)
               .name(this.apply(namecolormodule$coloroption.displayName(), map))
               .lore(this.apply(namecolormodule$coloroption.lore(), map))
               .hideAll()
               .build()
         );
      }

      int k = this.config.getInt("reset.slot", 4);
      inventory.setItem(
         k,
         new ItemBuilder(Material.BARRIER)
            .name(this.config.getString("reset.display-name", "&c&lReset name colour"))
            .lore(this.apply(this.config.getStringList("reset.lore"), map))
            .hideAll()
            .build()
      );
      player.openInventory(inventory);
   }

   public Map<String, String> equipPlaceholders(Player player) {
      Map<String, String> map = new LinkedHashMap<>();
      String s = this.config.getString("placeholders.owned-yes", "&#9FFF00&nYes");
      String s1 = this.config.getString("placeholders.owned-no", "&#FF2727&nNo");
      String s2 = this.config.getString("placeholders.none", "&7None");
      String s3 = this.database == null ? null : this.database.getLastGradient(player.getUniqueId());
      map.put("last_gradient", s3 != null && !s3.isBlank() ? s3 : s2);

      for (NameColorModule.ColorOption namecolormodule$coloroption : this.colors.values()) {
         map.put("namecolor_owned_" + namecolormodule$coloroption.id(), player.hasPermission(namecolormodule$coloroption.permission()) ? s : s1);
      }

      return map;
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (TrackedInventories.lookup(event.getView().getTopInventory(), NameColorModule.MenuHolder.class) != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               if (event.getSlot() == this.config.getInt("reset.slot", 4)) {
                  player.closeInventory();
                  this.resetColor(player);
               } else {
                  for (NameColorModule.ColorOption namecolormodule$coloroption : this.colors.values()) {
                     if (namecolormodule$coloroption.slot() == event.getSlot()) {
                        player.closeInventory();
                        this.applyColorById(player, namecolormodule$coloroption.id());
                        return;
                     }
                  }
               }
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (TrackedInventories.lookup(event.getView().getTopInventory(), NameColorModule.MenuHolder.class) != null) {
         event.setCancelled(true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      if (Boolean.TRUE.equals(this.awaitingGradient.remove(player.getUniqueId()))) {
         event.setCancelled(true);
         String s = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
         Bukkit.getScheduler().runTask(this.plugin, () -> this.handleGradientInput(player, s));
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.awaitingGradient.remove(event.getPlayer().getUniqueId());
   }

   private void handleGradientInput(Player player, String raw) {
      if (!player.hasPermission("sharded.namecolor.custom")) {
         this.send(player, "not-owned", new String[]{"%color%", "Custom"});
      } else {
         String s = this.normalizeGradientInput(raw);
         if (s == null) {
            this.send(player, "custom-invalid", new String[0]);
         } else {
            this.applyGradient(player, s, true);
         }
      }
   }

   private void applyGradient(Player player, String gradient, boolean fromChat) {
      CosmeticService cosmeticservice = this.plugin.cosmetics();
      if (cosmeticservice != null) {
         cosmeticservice.setNameColor(player, gradient);
      }

      if (this.database != null) {
         this.database.saveLastGradient(player.getUniqueId(), gradient);
      }

      this.send(player, fromChat ? "custom-set" : "custom-reapplied", new String[]{"%gradient%", gradient});
   }

   private String normalizeGradientInput(String raw) {
      if (raw != null && GRADIENT_INPUT.matcher(raw.trim()).matches()) {
         String[] astring = raw.trim().split("\\s+");
         return this.ensureHash(astring[0]) + " " + this.ensureHash(astring[1]);
      } else {
         return null;
      }
   }

   private String ensureHash(String hex) {
      return hex.startsWith("#") ? hex.toUpperCase(Locale.ROOT) : "#" + hex.toUpperCase(Locale.ROOT);
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         List<String> list = new ArrayList<>(List.of("set", "reset", "gradient", "custom"));
         if (sender.hasPermission("sharded.namecolor.admin")) {
            list.addAll(List.of("create", "delete"));
         }

         list.addAll(this.colors.keySet());
         return TabCompleteHelper.filter(args[0], list);
      } else if (args.length == 2 && args[0].equalsIgnoreCase("gradient")) {
         return sender.hasPermission("sharded.namecolor.admin") ? TabCompleteHelper.filter(args[1], this.colors.keySet()) : List.of();
      } else {
         return args.length != 2 || !args[0].equalsIgnoreCase("set") && !args[0].equalsIgnoreCase("delete")
            ? List.of()
            : TabCompleteHelper.filter(args[1], this.colors.keySet());
      }
   }

   private List<String> apply(List<String> lines, Map<String, String> ph) {
      List<String> list = new ArrayList<>();

      for (String s : lines) {
         list.add(this.apply(s, ph));
      }

      return list;
   }

   private String apply(String line, Map<String, String> ph) {
      String s = line;

      for (Entry<String, String> entry : ph.entrySet()) {
         s = s.replace("%" + entry.getKey() + "%", entry.getValue());
      }

      return s;
   }

   private static record ColorOption(
      String id, int slot, String permission, String value, String material, String displayName, List<String> lore, boolean customInput
   ) {
   }

   private static final class MenuHolder implements InventoryHolder {
      public Inventory getInventory() {
         return null;
      }
   }
}

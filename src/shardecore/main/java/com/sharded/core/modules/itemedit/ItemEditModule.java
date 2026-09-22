package com.sharded.core.modules.itemedit;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.FireworkEffect.Type;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Axolotl.Variant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

public final class ItemEditModule extends Module implements CommandExecutor, TabCompleter {
   private static final String USE = "sharded.itemedit.use";
   private static final String SERVER = "sharded.itemedit.server";
   private static final String STORAGE = "sharded.itemedit.storage";
   private static final DyeColor[] DYES = DyeColor.values();
   private static final Type[] FIREWORK_TYPES = Type.values();
   private static final List<String> IE_SUBS = List.of(
      "help",
      "name",
      "lore",
      "unbreakable",
      "glow",
      "hide",
      "tooltip",
      "color",
      "potion",
      "enchant",
      "unenchant",
      "attribute",
      "repair",
      "durability",
      "maxdurability",
      "cmd",
      "model",
      "rarity",
      "food",
      "wear",
      "compass",
      "axolotl",
      "tropic",
      "trim",
      "book",
      "firework",
      "banner"
   );
   private static final List<String> LORE_SUBS = List.of("add", "set", "insert", "remove", "clear", "copy", "paste");
   private static final List<String> SI_SUBS = List.of("save", "give", "list", "delete", "sell", "buy");
   private static final List<String> IS_SUBS = List.of("save", "load", "list", "delete");
   private ItemLibrary serverItems;
   private File playersDir;
   private final Map<UUID, ItemLibrary> playerLibs = new HashMap<>();
   private final Map<UUID, List<String>> loreClipboard = new HashMap<>();

   public ItemEditModule(ShardedCore plugin) {
      super(plugin, "itemedit");
   }

   @Override
   protected void onEnable() {
      this.serverItems = new ItemLibrary(new File(this.moduleFolder(), "server-items.yml"));
      this.serverItems.load();
      this.playersDir = new File(this.moduleFolder(), "players");
      this.playersDir.mkdirs();
      this.registerCommand("itemedit", this);
      this.registerCommand("serveritem", this);
      this.registerCommand("itemstorage", this);
      this.registerListener(this);
   }

   @Override
   protected void onDisable() {
      this.playerLibs.clear();
      this.loreClipboard.clear();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);

      return switch (s) {
         case "serveritem", "si" -> this.serverCommand(sender, args);
         case "itemstorage", "is" -> this.storageCommand(sender, args);
         default -> this.editCommand(sender, args);
      };
   }

   private boolean editCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("sharded.itemedit.use") && !sender.isOp()) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
         this.help(sender);
         return true;
      } else if (sender instanceof Player player) {
         ItemStack itemstack = player.getInventory().getItemInMainHand();
         if (!itemstack.getType().isAir() && itemstack.getAmount() > 0) {
            String s = args[0].toLowerCase(Locale.ROOT);

            ItemMutator.Outcome itemmutator$outcome = switch (s) {
               case "name", "rename" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.name(itemstack, join(args, 1));
               case "lore" -> this.lore(player, itemstack, args);
               case "unbreakable" -> ItemMutator.unbreakable(itemstack, args.length >= 2 ? ItemMutator.bool(args[1]) : null);
               case "glow" -> ItemMutator.glow(itemstack, args.length >= 2 ? ItemMutator.bool(args[1]) : null);
               case "hide" -> args.length < 2
               ? ItemMutator.Outcome.fail("usage-ie")
               : ItemMutator.hide(itemstack, args[1], args.length >= 3 ? ItemMutator.bool(args[2]) : null);
               case "tooltip", "tooltipstyle" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.tooltipStyle(itemstack, args[1]);
               case "color" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.color(itemstack, join(args, 1));
               case "potion" -> this.potion(itemstack, args);
               case "enchant" -> args.length < 3 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.enchant(itemstack, args[1], parseInt(args[2], 1));
               case "unenchant" -> ItemMutator.unenchant(itemstack, args.length >= 2 ? args[1] : "all");
               case "attribute", "attr" -> args.length < 3
               ? ItemMutator.Outcome.fail("usage-ie")
               : ItemMutator.attribute(itemstack, args[1], parseDouble(args[2], 0.0), args.length >= 4 ? args[3] : "add");
               case "repair" -> ItemMutator.repair(itemstack);
               case "durability", "damage" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.durability(itemstack, parseInt(args[1], 0));
               case "maxdurability", "maxdamage" -> args.length < 2
               ? ItemMutator.Outcome.fail("usage-ie")
               : ItemMutator.maxDurability(itemstack, parseInt(args[1], 1));
               case "cmd", "custommodeldata" -> args.length < 2
               ? ItemMutator.Outcome.fail("usage-ie")
               : ItemMutator.customModelData(itemstack, parseInt(args[1], 0));
               case "model", "itemmodel" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.itemModel(itemstack, args[1]);
               case "rarity" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.rarity(itemstack, args[1]);
               case "food" -> this.food(itemstack, args);
               case "wear", "equippable" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.wear(itemstack, args[1]);
               case "compass" -> this.compass(player, itemstack, args);
               case "axolotl" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.axolotl(itemstack, args[1]);
               case "tropic", "tropical", "fish" -> args.length < 4
               ? ItemMutator.Outcome.fail("usage-ie")
               : ItemMutator.tropic(itemstack, args[1], args[2], args[3]);
               case "trim" -> args.length < 3 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.trim(itemstack, args[1], args[2]);
               case "book" -> args.length < 2 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.book(itemstack, args[1]);
               case "firework", "fw" -> {
                  this.openFirework(player, itemstack);
                  yield null;
               }
               case "banner" -> {
                  this.openBanner(player, itemstack);
                  yield null;
               }
               default -> ItemMutator.Outcome.fail("unknown");
            };
            if (itemmutator$outcome != null) {
               this.send(sender, itemmutator$outcome.key(), itemmutator$outcome.vars());
               if (itemmutator$outcome.ok()) {
                  player.getInventory().setItemInMainHand(itemstack);
               }
            }

            return true;
         } else {
            this.send(sender, "need-item", new String[0]);
            return true;
         }
      } else {
         this.send(sender, "need-player", new String[0]);
         return true;
      }
   }

   private ItemMutator.Outcome lore(Player player, ItemStack stack, String[] args) {
      if (args.length < 2) {
         return ItemMutator.Outcome.fail("usage-ie");
      } else {
         String s = args[1].toLowerCase(Locale.ROOT);

         return switch (s) {
            case "add" -> args.length < 3 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.loreAdd(stack, join(args, 2));
            case "set" -> {
               if (args.length < 3) {
                  yield ItemMutator.Outcome.fail("usage-ie");
               } else {
                  Integer integer = tryInt(args[2]);
                  yield integer != null
                     ? (args.length < 4 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.loreSet(stack, integer, join(args, 3)))
                     : ItemMutator.loreReplaceAll(stack, join(args, 2));
               }
            }
            case "insert" -> args.length < 4 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.loreInsert(stack, parseInt(args[2], 1), join(args, 3));
            case "remove" -> args.length < 3 ? ItemMutator.Outcome.fail("usage-ie") : ItemMutator.loreRemove(stack, parseInt(args[2], 1));
            case "clear" -> ItemMutator.loreClear(stack);
            case "copy" -> {
               List<String> list = ItemMutator.loreCopy(stack);
               if (list.isEmpty()) {
                  yield ItemMutator.Outcome.fail("lore-empty");
               } else {
                  this.loreClipboard.put(player.getUniqueId(), list);
                  yield ItemMutator.Outcome.success();
               }
            }
            case "paste" -> ItemMutator.lorePaste(stack, this.loreClipboard.get(player.getUniqueId()));
            default -> ItemMutator.Outcome.fail("unknown");
         };
      }
   }

   private ItemMutator.Outcome potion(ItemStack stack, String[] args) {
      if (args.length < 2) {
         return ItemMutator.Outcome.fail("usage-ie");
      } else {
         String s = args[1].toLowerCase(Locale.ROOT);
         if (s.equals("clear")) {
            return ItemMutator.potionClear(stack);
         } else if (s.equals("hide")) {
            return ItemMutator.hide(stack, "potion", args.length >= 3 ? ItemMutator.bool(args[2]) : true);
         } else if (!s.equals("add") && !s.equals("effect")) {
            return ItemMutator.Outcome.fail("unknown");
         } else {
            return args.length < 4
               ? ItemMutator.Outcome.fail("usage-ie")
               : ItemMutator.potionAdd(stack, args[2], parseInt(args[3], 30), args.length >= 5 ? parseInt(args[4], 0) : 0);
         }
      }
   }

   private ItemMutator.Outcome food(ItemStack stack, String[] args) {
      if (args.length < 3) {
         return ItemMutator.Outcome.fail("usage-ie");
      } else {
         boolean flag = false;
         boolean flag1 = false;

         for (int i = 3; i < args.length; i++) {
            String s = args[i].toLowerCase(Locale.ROOT);
            if (s.equals("always") || s.equals("true") || s.equals("yes")) {
               flag = true;
            } else if (s.equals("drink")) {
               flag1 = true;
            } else if (s.equals("eat")) {
               flag1 = false;
            }
         }

         return ItemMutator.food(stack, parseInt(args[1], 4), (float)parseDouble(args[2], 0.6), flag, flag1);
      }
   }

   private ItemMutator.Outcome compass(Player player, ItemStack stack, String[] args) {
      if (args.length < 4) {
         return ItemMutator.Outcome.fail("usage-ie");
      } else {
         String s = args.length >= 5 ? args[4] : player.getWorld().getName();
         return ItemMutator.compass(stack, parseDouble(args[1], 0.0), parseDouble(args[2], 0.0), parseDouble(args[3], 0.0), s);
      }
   }

   private boolean serverCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("sharded.itemedit.server") && !sender.isOp()) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(sender, "usage-si", new String[0]);
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         switch (s) {
            case "save":
               if (!(sender instanceof Player player3)) {
                  this.send(sender, "need-player", new String[0]);
                  return true;
               }

               if (args.length < 2 || !ItemLibrary.validId(args[1])) {
                  this.send(sender, "bad-id", new String[0]);
                  return true;
               }

               ItemStack itemstack1 = player3.getInventory().getItemInMainHand();
               if (itemstack1.getType().isAir()) {
                  this.send(sender, "need-item", new String[0]);
                  return true;
               }

               if (!this.serverItems.contains(ItemLibrary.normalizeId(args[1])) && this.serverItems.size() >= this.config.getInt("max-server-items", 256)) {
                  this.send(sender, "limit", new String[]{"%max%", String.valueOf(this.config.getInt("max-server-items", 256))});
                  return true;
               }

               String s5 = ItemLibrary.normalizeId(args[1]);
               this.serverItems.put(s5, itemstack1);
               this.send(sender, "saved", new String[]{"%id%", s5});
               break;
            case "give":
               if (args.length < 3) {
                  this.send(sender, "usage-si", new String[0]);
                  return true;
               }

               Player player2 = Bukkit.getPlayerExact(args[1]);
               if (player2 == null) {
                  this.send(sender, "need-player", new String[0]);
                  return true;
               }

               String s4 = ItemLibrary.normalizeId(args[2]);
               ItemStack itemstack2 = this.serverItems.get(s4);
               if (itemstack2 == null) {
                  this.send(sender, "missing", new String[]{"%id%", s4});
                  return true;
               }

               int k = args.length >= 4 ? Math.max(1, parseInt(args[3], 1)) : itemstack2.getAmount();
               itemstack2.setAmount(Math.min(itemstack2.getMaxStackSize(), k));
               this.deliver(player2, itemstack2);
               this.send(sender, "given", new String[]{"%id%", s4, "%player%", player2.getName()});
               break;
            case "list":
               this.list(sender, this.serverItems, true);
               break;
            case "delete":
            case "remove":
               if (args.length < 2) {
                  this.send(sender, "usage-si", new String[0]);
                  return true;
               }

               String s3 = ItemLibrary.normalizeId(args[1]);
               if (!this.serverItems.contains(s3)) {
                  this.send(sender, "missing", new String[]{"%id%", s3});
                  return true;
               }

               this.serverItems.delete(s3);
               this.send(sender, "deleted", new String[]{"%id%", s3});
               break;
            case "sell":
               if (args.length < 3) {
                  this.send(sender, "usage-si", new String[0]);
                  return true;
               }

               String s2 = ItemLibrary.normalizeId(args[1]);
               if (!this.serverItems.contains(s2)) {
                  this.send(sender, "missing", new String[]{"%id%", s2});
                  return true;
               }

               long j = Math.max(0L, (long)parseDouble(args[2], 0.0));
               this.serverItems.setPrice(s2, j);
               this.send(sender, "sold", new String[]{"%id%", s2, "%price%", String.valueOf(j)});
               break;
            case "buy":
               if (args.length < 2) {
                  this.send(sender, "usage-si", new String[0]);
                  return true;
               }

               Player player;
               String s1;
               if (args.length >= 3) {
                  player = Bukkit.getPlayerExact(args[1]);
                  s1 = ItemLibrary.normalizeId(args[2]);
               } else {
                  if (!(sender instanceof Player player1)) {
                     this.send(sender, "need-player", new String[0]);
                     return true;
                  }

                  player = player1;
                  s1 = ItemLibrary.normalizeId(args[1]);
               }

               if (player == null) {
                  this.send(sender, "need-player", new String[0]);
                  return true;
               }

               ItemStack itemstack = this.serverItems.get(s1);
               if (itemstack == null) {
                  this.send(sender, "missing", new String[]{"%id%", s1});
                  return true;
               }

               long i = this.serverItems.price(s1);
               if (i < 0L) {
                  this.send(sender, "no-price", new String[]{"%id%", s1});
                  return true;
               }

               TokenService tokenservice = this.plugin.modules() == null ? null : this.plugin.modules().tokens();
               if (tokenservice == null || !tokenservice.take(player.getUniqueId(), i)) {
                  this.send(sender, "cannot-afford", new String[]{"%price%", String.valueOf(i)});
                  return true;
               }

               this.deliver(player, itemstack);
               this.send(sender, "bought", new String[]{"%id%", s1, "%price%", String.valueOf(i), "%player%", player.getName()});
               break;
            default:
               this.send(sender, "usage-si", new String[0]);
         }

         return true;
      }
   }

   private boolean storageCommand(CommandSender sender, String[] args) {
      if (!sender.hasPermission("sharded.itemedit.storage") && !sender.isOp()) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (!(sender instanceof Player player)) {
         this.send(sender, "need-player", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(sender, "usage-is", new String[0]);
         return true;
      } else {
         ItemLibrary itemlibrary = this.playerLib(player.getUniqueId());
         String s = args[0].toLowerCase(Locale.ROOT);
         switch (s) {
            case "save":
               if (args.length < 2 || !ItemLibrary.validId(args[1])) {
                  this.send(sender, "bad-id", new String[0]);
                  return true;
               }

               ItemStack itemstack1 = player.getInventory().getItemInMainHand();
               if (itemstack1.getType().isAir()) {
                  this.send(sender, "need-item", new String[0]);
                  return true;
               }

               String s3 = ItemLibrary.normalizeId(args[1]);
               if (!itemlibrary.contains(s3) && itemlibrary.size() >= this.config.getInt("max-saved-items-per-player", 64)) {
                  this.send(sender, "limit", new String[]{"%max%", String.valueOf(this.config.getInt("max-saved-items-per-player", 64))});
                  return true;
               }

               itemlibrary.put(s3, itemstack1);
               this.send(sender, "saved", new String[]{"%id%", s3});
               break;
            case "load":
               if (args.length < 2) {
                  this.send(sender, "usage-is", new String[0]);
                  return true;
               }

               String s2 = ItemLibrary.normalizeId(args[1]);
               ItemStack itemstack = itemlibrary.get(s2);
               if (itemstack == null) {
                  this.send(sender, "missing", new String[]{"%id%", s2});
                  return true;
               }

               this.deliver(player, itemstack);
               this.send(sender, "loaded", new String[]{"%id%", s2});
               break;
            case "list":
               this.list(sender, itemlibrary, false);
               break;
            case "delete":
            case "remove":
               if (args.length < 2) {
                  this.send(sender, "usage-is", new String[0]);
                  return true;
               }

               String s1 = ItemLibrary.normalizeId(args[1]);
               if (!itemlibrary.contains(s1)) {
                  this.send(sender, "missing", new String[]{"%id%", s1});
                  return true;
               }

               itemlibrary.delete(s1);
               this.send(sender, "deleted", new String[]{"%id%", s1});
               break;
            default:
               this.send(sender, "usage-is", new String[0]);
         }

         return true;
      }
   }

   private void list(CommandSender sender, ItemLibrary library, boolean prices) {
      List<String> list = library.ids();
      if (list.isEmpty()) {
         this.send(sender, "list-empty", new String[0]);
      } else {
         this.send(sender, "list-header", new String[]{"%count%", String.valueOf(list.size())});

         for (String s : list) {
            String s1 = "";
            if (prices) {
               long i = library.price(s);
               s1 = i >= 0L ? "&8(&f" + i + " tokens&8)" : "&8(&7no price&8)";
            }

            this.send(sender, "list-line", new String[]{"%id%", s, "%price%", s1});
         }
      }
   }

   private void deliver(Player player, ItemStack item) {
      Map<Integer, ItemStack> map = player.getInventory().addItem(new ItemStack[]{item});
      if (!map.isEmpty()) {
         for (ItemStack itemstack : map.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), itemstack);
         }

         this.send(player, "inventory-full", new String[0]);
      }
   }

   private ItemLibrary playerLib(UUID id) {
      return this.playerLibs.computeIfAbsent(id, uuid -> {
         ItemLibrary itemlibrary = new ItemLibrary(new File(this.playersDir, uuid + ".yml"));
         itemlibrary.load();
         return itemlibrary;
      });
   }

   private void help(CommandSender sender) {
      for (String s : this.rawList("help", new String[0])) {
         sender.sendMessage(Text.c(s));
      }
   }

   private void openFirework(Player player, ItemStack stack) {
      if (!(stack.getItemMeta() instanceof FireworkMeta) && !(stack.getItemMeta() instanceof FireworkEffectMeta)) {
         this.send(player, "not-firework", new String[0]);
      } else {
         ItemEditModule.FireworkSession itemeditmodule$fireworksession = ItemEditModule.FireworkSession.from(stack);
         Inventory inventory = Bukkit.createInventory(new ItemEditModule.MenuHolder("firework"), 54, Text.c("&dFirework editor"));
         TrackedInventories.track(inventory, itemeditmodule$fireworksession);
         this.drawFirework(inventory, itemeditmodule$fireworksession);
         player.openInventory(inventory);
         this.send(player, "opened-firework", new String[0]);
      }
   }

   private void openBanner(Player player, ItemStack stack) {
      if (stack.getItemMeta() instanceof BannerMeta && stack.getType().name().endsWith("_BANNER")) {
         ItemEditModule.BannerSession itemeditmodule$bannersession = ItemEditModule.BannerSession.from(stack);
         Inventory inventory = Bukkit.createInventory(new ItemEditModule.MenuHolder("banner"), 54, Text.c("&6Banner editor"));
         TrackedInventories.track(inventory, itemeditmodule$bannersession);
         this.drawBanner(inventory, itemeditmodule$bannersession);
         player.openInventory(inventory);
         this.send(player, "opened-banner", new String[0]);
      } else {
         this.send(player, "not-banner", new String[0]);
      }
   }

   private void drawFirework(Inventory inv, ItemEditModule.FireworkSession session) {
      inv.clear();

      for (int i = 0; i < DYES.length && i < 16; i++) {
         DyeColor dyecolor = DYES[i];
         inv.setItem(
            i,
            new ItemBuilder(dyeMaterial(dyecolor, "_DYE"))
               .name("&f" + pretty(dyecolor.name()))
               .lore("&7Left-click: add color", "&7Right-click: add fade")
               .hideAll()
               .build()
         );
      }

      inv.setItem(
         16,
         new ItemBuilder(session.flicker ? Material.GLOWSTONE_DUST : Material.GUNPOWDER)
            .name(session.flicker ? "&eFlicker &aON" : "&eFlicker &cOFF")
            .hideAll()
            .build()
      );
      inv.setItem(
         17, new ItemBuilder(session.trail ? Material.DIAMOND : Material.FIRE_CHARGE).name(session.trail ? "&bTrail &aON" : "&bTrail &cOFF").hideAll().build()
      );

      for (int j = 0; j < FIREWORK_TYPES.length && j < 5; j++) {
         Type type = FIREWORK_TYPES[j];
         boolean flag = session.type == type;
         inv.setItem(19 + j, new ItemBuilder(Material.FIREWORK_STAR).name((flag ? "&a" : "&f") + pretty(type.name())).glow(flag).hideAll().build());
      }

      inv.setItem(25, new ItemBuilder(Material.RED_DYE).name("&cPower -").lore("&7Current: &f" + session.power).hideAll().build());
      inv.setItem(26, new ItemBuilder(Material.LIME_DYE).name("&aPower +").lore("&7Current: &f" + session.power).hideAll().build());
      inv.setItem(37, new ItemBuilder(Material.EMERALD).name("&aAdd effect").hideAll().build());
      inv.setItem(38, new ItemBuilder(Material.BARRIER).name("&cUndo last effect").hideAll().build());
      inv.setItem(39, new ItemBuilder(Material.TNT).name("&cClear effects").hideAll().build());
      inv.setItem(40, session.preview());
      inv.setItem(48, new ItemBuilder(Material.LIME_CONCRETE).name("&aApply to hand").hideAll().build());
      inv.setItem(50, new ItemBuilder(Material.RED_CONCRETE).name("&cClose").hideAll().build());
   }

   private void drawBanner(Inventory inv, ItemEditModule.BannerSession session) {
      inv.clear();

      for (int i = 0; i < DYES.length && i < 16; i++) {
         DyeColor dyecolor = DYES[i];
         boolean flag = session.dye == dyecolor;
         inv.setItem(
            i,
            new ItemBuilder(dyeMaterial(dyecolor, "_DYE"))
               .name((flag ? "&a" : "&f") + pretty(dyecolor.name()))
               .lore("&7Click: select dye", "&7Shift-click: set banner base")
               .glow(flag)
               .hideAll()
               .build()
         );
      }

      List<PatternType> list = Registry.BANNER_PATTERN.stream().toList();
      int j = 18;

      for (int k = 0; k < list.size() && j < 45; k++) {
         PatternType patterntype = list.get(k);
         inv.setItem(
            j, new ItemBuilder(Material.WHITE_BANNER).name("&f" + pretty(patterntype.getKey().getKey())).lore("&7Click to add this pattern").edit(meta -> {
               if (meta instanceof BannerMeta bannermeta) {
                  bannermeta.addPattern(new Pattern(session.dye, patterntype));
               }
            }).hideAll().build()
         );
         j++;
      }

      inv.setItem(45, new ItemBuilder(Material.BARRIER).name("&cUndo last pattern").hideAll().build());
      inv.setItem(46, new ItemBuilder(Material.TNT).name("&cClear patterns").hideAll().build());
      inv.setItem(49, session.preview());
      inv.setItem(48, new ItemBuilder(Material.LIME_CONCRETE).name("&aApply to hand").hideAll().build());
      inv.setItem(50, new ItemBuilder(Material.RED_CONCRETE).name("&cClose").hideAll().build());
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         Inventory inventory = event.getView().getTopInventory();
         ItemEditModule.FireworkSession firework = TrackedInventories.lookup(inventory, ItemEditModule.FireworkSession.class);
         ItemEditModule.BannerSession banner = TrackedInventories.lookup(inventory, ItemEditModule.BannerSession.class);
         if (firework != null || banner != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == inventory) {
               int i = event.getSlot();
               if (firework != null) {
                  this.clickFirework(player, inventory, firework, i, event.getClick());
               } else {
                  this.clickBanner(player, inventory, banner, i, event.getClick());
               }
            }
         }
      }
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      Inventory inventory = event.getView().getTopInventory();
      if (TrackedInventories.lookup(inventory, ItemEditModule.FireworkSession.class) != null
         || TrackedInventories.lookup(inventory, ItemEditModule.BannerSession.class) != null) {
         event.setCancelled(true);
      }
   }

   private void clickFirework(Player player, Inventory inv, ItemEditModule.FireworkSession session, int slot, ClickType click) {
      if (slot >= 0 && slot < 16 && slot < DYES.length) {
         Color color = DYES[slot].getColor();
         if (click.isRightClick()) {
            session.fades.add(color);
         } else {
            session.colors.add(color);
         }

         this.drawFirework(inv, session);
      } else if (slot == 16) {
         session.flicker = !session.flicker;
         this.drawFirework(inv, session);
      } else if (slot == 17) {
         session.trail = !session.trail;
         this.drawFirework(inv, session);
      } else if (slot >= 19 && slot <= 23) {
         int i = slot - 19;
         if (i < FIREWORK_TYPES.length) {
            session.type = FIREWORK_TYPES[i];
            this.drawFirework(inv, session);
         }
      } else if (slot == 25) {
         session.power = Math.max(0, session.power - 1);
         this.drawFirework(inv, session);
      } else if (slot == 26) {
         session.power = Math.min(7, session.power + 1);
         this.drawFirework(inv, session);
      } else if (slot == 37) {
         session.addCurrent();
         this.drawFirework(inv, session);
      } else if (slot == 38) {
         if (!session.effects.isEmpty()) {
            session.effects.remove(session.effects.size() - 1);
         }

         this.drawFirework(inv, session);
      } else if (slot == 39) {
         session.effects.clear();
         this.drawFirework(inv, session);
      } else if (slot == 48) {
         ItemStack itemstack = player.getInventory().getItemInMainHand();
         if (!session.apply(itemstack)) {
            this.send(player, "not-firework", new String[0]);
         } else {
            player.getInventory().setItemInMainHand(itemstack);
            player.closeInventory();
            this.send(player, "applied", new String[0]);
         }
      } else {
         if (slot == 50) {
            player.closeInventory();
         }
      }
   }

   private void clickBanner(Player player, Inventory inv, ItemEditModule.BannerSession session, int slot, ClickType click) {
      if (slot >= 0 && slot < 16 && slot < DYES.length) {
         DyeColor dyecolor = DYES[slot];
         if (click.isShiftClick()) {
            session.base = dyecolor;
         } else {
            session.dye = dyecolor;
         }

         this.drawBanner(inv, session);
      } else if (slot >= 18 && slot < 45) {
         List<PatternType> list = Registry.BANNER_PATTERN.stream().toList();
         int i = slot - 18;
         if (i >= 0 && i < list.size()) {
            session.patterns.add(new Pattern(session.dye, list.get(i)));
            this.drawBanner(inv, session);
         }
      } else if (slot == 45) {
         if (!session.patterns.isEmpty()) {
            session.patterns.remove(session.patterns.size() - 1);
         }

         this.drawBanner(inv, session);
      } else if (slot == 46) {
         session.patterns.clear();
         this.drawBanner(inv, session);
      } else if (slot == 48) {
         ItemStack itemstack = player.getInventory().getItemInMainHand();
         if (!session.apply(itemstack)) {
            this.send(player, "not-banner", new String[0]);
         } else {
            player.getInventory().setItemInMainHand(itemstack);
            player.closeInventory();
            this.send(player, "applied", new String[0]);
         }
      } else {
         if (slot == 50) {
            player.closeInventory();
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("serveritem") || s.equals("si")) {
         if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], SI_SUBS);
         } else if (args.length != 2 || !args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("buy")) {
            return args.length != 2 && (args.length != 3 || !args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("buy"))
               ? List.of()
               : TabCompleteHelper.filter(args[args.length - 1], this.serverItems.ids());
         } else {
            return TabCompleteHelper.onlinePlayers(args[1]);
         }
      } else if (!s.equals("itemstorage") && !s.equals("is")) {
         if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], IE_SUBS);
         } else {
            String s1 = args[0].toLowerCase(Locale.ROOT);
            if (args.length == 2) {
               return switch (s1) {
                  case "lore" -> TabCompleteHelper.filter(args[1], LORE_SUBS);
                  case "hide" -> TabCompleteHelper.filter(args[1], "flags", "enchants", "potion", "tooltip", "attributes", "unbreakable", "dye", "trim");
                  case "unbreakable", "glow" -> TabCompleteHelper.filter(args[1], "true", "false");
                  case "potion" -> TabCompleteHelper.filter(args[1], "add", "clear", "hide");
                  case "enchant", "unenchant" -> TabCompleteHelper.filter(args[1], enchantNames());
                  case "attribute", "attr" -> TabCompleteHelper.filter(args[1], attributeNames());
                  case "rarity" -> TabCompleteHelper.filter(args[1], rarityNames());
                  case "wear" -> TabCompleteHelper.filter(args[1], "head", "chest", "legs", "feet", "offhand", "body");
                  case "axolotl" -> TabCompleteHelper.filter(args[1], enumNames(Variant.class));
                  case "tropic", "tropical", "fish" -> TabCompleteHelper.filter(args[1], enumNames(org.bukkit.entity.TropicalFish.Pattern.class));
                  case "trim" -> TabCompleteHelper.filter(args[1], keyNames(Registry.TRIM_PATTERN));
                  case "book" -> TabCompleteHelper.filter(args[1], "original", "copy", "copy_of_copy", "tattered");
                  case "color" -> TabCompleteHelper.filter(args[1], enumNames(DyeColor.class));
                  default -> List.of();
               };
            } else if (args.length == 3 && s1.equals("potion") && args[1].equalsIgnoreCase("add")) {
               return TabCompleteHelper.filter(args[2], effectNames());
            } else if (args.length != 3 || !s1.equals("tropic") && !s1.equals("tropical") && !s1.equals("fish")) {
               if (args.length != 4 || !s1.equals("tropic") && !s1.equals("tropical") && !s1.equals("fish")) {
                  if (args.length == 3 && s1.equals("trim")) {
                     return TabCompleteHelper.filter(args[2], keyNames(Registry.TRIM_MATERIAL));
                  } else {
                     return args.length != 4 || !s1.equals("attribute") && !s1.equals("attr")
                        ? List.of()
                        : TabCompleteHelper.filter(args[3], "add", "multiply", "multiply_base");
                  }
               } else {
                  return TabCompleteHelper.filter(args[3], enumNames(DyeColor.class));
               }
            } else {
               return TabCompleteHelper.filter(args[2], enumNames(DyeColor.class));
            }
         }
      } else if (sender instanceof Player player) {
         if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], IS_SUBS);
         } else {
            return args.length == 2 && !args[0].equalsIgnoreCase("save") && !args[0].equalsIgnoreCase("list")
               ? TabCompleteHelper.filter(args[1], this.playerLib(player.getUniqueId()).ids())
               : List.of();
         }
      } else {
         return List.of();
      }
   }

   private static List<String> enchantNames() {
      return Registry.ENCHANTMENT.stream().map(enchant -> enchant.getKey().getKey()).sorted().toList();
   }

   private static List<String> attributeNames() {
      return Registry.ATTRIBUTE.stream().map(attr -> attr.getKey().getKey()).sorted().toList();
   }

   private static List<String> effectNames() {
      return Registry.POTION_EFFECT_TYPE.stream().map(effect -> effect.getKey().getKey()).sorted().toList();
   }

   private static List<String> rarityNames() {
      return Stream.of(ItemRarity.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
   }

   private static <E extends Enum<E>> List<String> enumNames(Class<E> type) {
      return Stream.of(type.getEnumConstants()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList();
   }

   private static <T extends Keyed> List<String> keyNames(Registry<T> registry) {
      return registry.stream().map(value -> value.getKey().getKey()).sorted().toList();
   }

   private static String join(String[] args, int from) {
      return String.join(" ", Arrays.copyOfRange(args, from, args.length));
   }

   private static int parseInt(String raw, int fallback) {
      try {
         return Integer.parseInt(raw);
      } catch (NumberFormatException numberformatexception) {
         return fallback;
      }
   }

   private static double parseDouble(String raw, double fallback) {
      try {
         return Double.parseDouble(raw);
      } catch (NumberFormatException numberformatexception) {
         return fallback;
      }
   }

   private static Integer tryInt(String raw) {
      try {
         return Integer.parseInt(raw);
      } catch (NumberFormatException numberformatexception) {
         return null;
      }
   }

   private static String pretty(String raw) {
      String s = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
      return s.isEmpty() ? raw : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   private static Material dyeMaterial(DyeColor dye, String suffix) {
      Material material = Material.matchMaterial(dye.name() + suffix);
      return material == null ? Material.WHITE_DYE : material;
   }

   private static Material bannerMaterial(DyeColor dye) {
      Material material = Material.matchMaterial(dye.name() + "_BANNER");
      return material == null ? Material.WHITE_BANNER : material;
   }

   static final class BannerSession {
      DyeColor base = DyeColor.WHITE;
      DyeColor dye = DyeColor.BLACK;
      final List<Pattern> patterns = new ArrayList<>();

      static ItemEditModule.BannerSession from(ItemStack stack) {
         ItemEditModule.BannerSession itemeditmodule$bannersession = new ItemEditModule.BannerSession();
         String s = stack.getType().name();
         if (s.endsWith("_BANNER")) {
            try {
               itemeditmodule$bannersession.base = DyeColor.valueOf(s.substring(0, s.length() - "_BANNER".length()));
            } catch (IllegalArgumentException illegalargumentexception) {
               itemeditmodule$bannersession.base = DyeColor.WHITE;
            }
         }

         if (stack.getItemMeta() instanceof BannerMeta bannermeta) {
            itemeditmodule$bannersession.patterns.addAll(bannermeta.getPatterns());
         }

         return itemeditmodule$bannersession;
      }

      ItemStack preview() {
         ItemStack itemstack = new ItemStack(ItemEditModule.bannerMaterial(this.base));
         if (itemstack.getItemMeta() instanceof BannerMeta bannermeta) {
            bannermeta.setPatterns(this.patterns);
            bannermeta.displayName(Text.c("&6Preview"));
            itemstack.setItemMeta(bannermeta);
         }

         return itemstack;
      }

      boolean apply(ItemStack hand) {
         Material material = ItemEditModule.bannerMaterial(this.base);
         if (hand.getType() != material) {
            hand.setType(material);
         }

         if (hand.getItemMeta() instanceof BannerMeta bannermeta) {
            bannermeta.setPatterns(this.patterns);
            hand.setItemMeta(bannermeta);
            return true;
         } else {
            return false;
         }
      }
   }

   static final class FireworkSession {
      Type type = Type.BALL;
      final List<Color> colors = new ArrayList<>();
      final List<Color> fades = new ArrayList<>();
      final List<FireworkEffect> effects = new ArrayList<>();
      boolean flicker;
      boolean trail;
      int power = 1;

      static ItemEditModule.FireworkSession from(ItemStack stack) {
         ItemEditModule.FireworkSession itemeditmodule$fireworksession = new ItemEditModule.FireworkSession();
         if (stack.getItemMeta() instanceof FireworkMeta fireworkmeta) {
            itemeditmodule$fireworksession.effects.addAll(fireworkmeta.getEffects());
            itemeditmodule$fireworksession.power = fireworkmeta.getPower();
         } else if (stack.getItemMeta() instanceof FireworkEffectMeta fireworkeffectmeta && fireworkeffectmeta.hasEffect()) {
            itemeditmodule$fireworksession.effects.add(fireworkeffectmeta.getEffect());
         }

         if (itemeditmodule$fireworksession.colors.isEmpty()) {
            itemeditmodule$fireworksession.colors.add(Color.WHITE);
         }

         return itemeditmodule$fireworksession;
      }

      void addCurrent() {
         if (this.colors.isEmpty()) {
            this.colors.add(Color.WHITE);
         }

         Builder builder = FireworkEffect.builder().with(this.type).flicker(this.flicker).trail(this.trail).withColor(this.colors);
         if (!this.fades.isEmpty()) {
            builder.withFade(this.fades);
         }

         this.effects.add(builder.build());
         this.colors.clear();
         this.fades.clear();
         this.colors.add(Color.WHITE);
      }

      ItemStack preview() {
         ItemStack itemstack = new ItemStack(Material.FIREWORK_ROCKET);
         if (itemstack.getItemMeta() instanceof FireworkMeta fireworkmeta) {
            fireworkmeta.addEffects(this.effects);
            fireworkmeta.setPower(this.power);
            fireworkmeta.displayName(Text.c("&dPreview"));
            itemstack.setItemMeta(fireworkmeta);
         }

         return itemstack;
      }

      boolean apply(ItemStack hand) {
         if (hand.getItemMeta() instanceof FireworkMeta fireworkmeta) {
            fireworkmeta.clearEffects();
            fireworkmeta.addEffects(this.effects);
            fireworkmeta.setPower(this.power);
            hand.setItemMeta(fireworkmeta);
            return true;
         } else if (hand.getItemMeta() instanceof FireworkEffectMeta fireworkeffectmeta) {
            if (this.effects.isEmpty()) {
               this.addCurrent();
            }

            fireworkeffectmeta.setEffect(this.effects.get(this.effects.size() - 1));
            hand.setItemMeta(fireworkeffectmeta);
            return true;
         } else {
            return false;
         }
      }
   }

   static final class MenuHolder implements InventoryHolder {
      final String kind;

      MenuHolder(String kind) {
         this.kind = kind;
      }

      public Inventory getInventory() {
         return null;
      }
   }
}

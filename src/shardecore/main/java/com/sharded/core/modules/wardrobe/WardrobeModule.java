package com.sharded.core.modules.wardrobe;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.ItemsAdderHook;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class WardrobeModule extends Module implements CommandExecutor, TabCompleter {
   private static final int[] CONTENT = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
   private static final String TAB_ALL = "all";
   private static final String TAB_LIMITED = "limited";
   private static final String TAB_FAVOURITES = "favourites";
   private static final String MENU_TITLE = "Wardrobe";
   private final Map<String, WardrobeModule.HatOption> hats = new LinkedHashMap<>();
   private final Set<UUID> hatWearers = new HashSet<>();
   private final Map<UUID, Boolean> rightClicks = new HashMap<>();
   private final Set<UUID> clickGuard = ConcurrentHashMap.newKeySet();
   private WardrobeDatabase database;
   private NamespacedKey hatKey;
   private WardrobeModule.HatGuard hatGuard;
   private WardrobeModule.LifecycleListener lifecycleListener;

   public WardrobeModule(ShardedCore plugin) {
      super(plugin, "wardrobe");
   }

   @Override
   protected void onEnable() {
      this.hatKey = new NamespacedKey(this.plugin, "wardrobe_hat");

      try {
         this.database = new WardrobeDatabase(this.plugin, this.moduleFolder());
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open wardrobe database", exception);
      }

      this.ensureCatalog();
      this.loadHats();
      this.hatGuard = new WardrobeModule.HatGuard();
      this.lifecycleListener = new WardrobeModule.LifecycleListener();
      this.registerListener(this.hatGuard);
      this.registerListener(this.lifecycleListener);
      this.registerCommand("wardrobe", this);
      this.registerCommand("hatshop", this);
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::logItemsAdderState, 40L);
      this.plugin.getServer().getScheduler().runTaskLater(this.plugin, this::logItemsAdderState, 120L);

      for (Player player : this.plugin.getServer().getOnlinePlayers()) {
         this.syncWearerState(player);
      }
   }

   private void logItemsAdderState() {
      List<String> list = ItemsAdderHook.listRegistryIds();
      this.plugin.getLogger().info("[wardrobe] ItemsAdder " + (ItemsAdderHook.isAvailable() ? "ready" : "missing") + " registry=" + list.size());
   }

   @Override
   protected void onDisable() {
      if (this.hatGuard != null) {
         HandlerList.unregisterAll(this.hatGuard);
      }

      this.hatWearers.clear();
      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
   }

   private void ensureCatalog() {
      boolean flag = false;
      if (this.config.getInt("menu-rows", 4) < 6) {
         this.config.set("menu-rows", 6);
         flag = true;
      }

      if (!this.config.contains("limited-lore")) {
         this.config.set("limited-lore", "&e&lLimited hat will be gone soon");
         flag = true;
      }

      if (!this.config.contains("default-price")) {
         this.config.set("default-price", 800);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 9) {
         this.config.set("remove.material", "BARRIER");
         this.config.set("remove.slot", 4);
         this.config.set("remove.display-name", "&#FF0000&lREMOVE HAT");
         this.config
            .set(
               "remove.lore",
               List.of(
                  "&8Description",
                  "",
                  "&#FF0000Information:",
                  "&#FF0000| &fClick here",
                  "&#FF0000| &fto Remove Your Cosmetic.",
                  "",
                  "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Remove"
               )
            );
         this.config.set("extra.enabled", true);
         this.config.set("extra.slot", 49);
         this.config.set("extra.material", "CLOCK");
         this.config.set("extra.name", "&#FFBA00&lLIMITED HATS");
         this.config
            .set(
               "extra.lore",
               List.of(
                  "&8Description",
                  "",
                  "&#FFBA00Information:",
                  "&#FFBA00| &fView limited",
                  "&#FFBA00| &ftime hats",
                  "",
                  "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To View"
               )
            );
         this.config.set("favourites.slot", 51);
         this.config.set("favourites.material", "NETHER_STAR");
         this.config.set("favourites.name", "&#FFBA00&lFAVOURITES");
         this.config
            .set(
               "favourites.lore",
               List.of(
                  "&8Description",
                  "",
                  "&#FFBA00Information:",
                  "&#FFBA00| &fHats you marked",
                  "&#FFBA00| &fas favourites",
                  "",
                  "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To View"
               )
            );
         this.config.set("status-owned", "&#94FF00&l&nOWNED");
         this.config.set("status-locked", "&#FF0000&l&nLOCKED");
         this.config
            .set(
               "hat-lore",
               List.of(
                  "&8Description",
                  "",
                  "%color%Information:",
                  "%color%| &fClick to equip",
                  "%color%| &fthis Cosmetic As Your Helmet",
                  "",
                  "%color%⚓ &fStatus: %status%",
                  "",
                  "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Equip"
               )
            );
         this.config.set("favourites-title", "Wardrobe | Favourites");
         this.config.set("shop-favourites-title", "Token Shop | Favourites");
         this.config.set("config-version", 9);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 11) {
         this.config.set("gui-title", "Wardrobe");
         this.config.set("limited-title", "Wardrobe | Limited");
         this.config.set("favourites-title", "Wardrobe | Favourites");
         this.config.set("shop-title", "Token Shop | Cosmetics");
         this.config.set("shop-limited-title", "Token Shop | Limited");
         this.config.set("shop-favourites-title", "Token Shop | Favourites");
         if (!this.config.contains("favourite-lore")) {
            this.config.set("favourite-lore", "&#FF55FF⚓ &fFavourited");
            this.config.set("favourite-click-lore", "&7Right-click to favourite");
            this.config.set("unfavourite-click-lore", "&7Right-click to unfavourite");
         }

         this.config.set("config-version", 11);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 12) {
         this.config.set("favourites.material", "CHEST_MINECART");
         this.config.set("favourite-click-lore", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nRIGHT-CLICK&r &x&F&F&B&A&0&0To Favourite");
         this.config.set("unfavourite-click-lore", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nRIGHT-CLICK&r &x&F&F&B&A&0&0To Favourite");
         this.config.set(
            "hat-lore",
            List.of(
               "&8Description",
               "",
               "%color%Information:",
               "%color%| &fClick to equip",
               "%color%| &fthis Cosmetic As Your Helmet",
               "",
               "%color%⚓ &fStatus: %status%",
               "",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nLEFT-CLICK&r &x&F&F&B&A&0&0To Equip"
            )
         );
         this.config.set("hats.tophat.color", "&#FF0053");
         this.config.set("hats.tophat.itemsadder-id", "hats:tophat");
         this.config.set("hats.top_hat.color", "&#FFD54A");
         this.config.set("hats.top_hat.display-name", "&x&F&F&D&5&4&A&lTOP HAT");
         this.config.set("hats.top_hat.itemsadder-id", "somehats:top_hat");
         this.config.set("hats.propeller_hat.color", "&#008DFF");
         this.config.set("hats.propeller_hat.itemsadder-id", "hats:propeller_hat");
         this.config.set("hats.propeller_hat.display-name", "&x&0&0&8&D&F&F&lPROPELLER HAT");
         this.config.set("hats.clown_mask.color", "&#FF3A3A");
         this.config.set("hats.clown_mask.itemsadder-id", "hats:clown_mask");
         this.config.set("hats.clown_mask.display-name", "&x&F&F&3&A&3&A&lCLOWN MASK");
         this.config.set("hats.sstraw_hat.color", "&#B3FF00");
         this.config.set("hats.sstraw_hat.itemsadder-id", "hats:straw_hat");
         this.config.set("hats.sstraw_hat.display-name", "&x&B&3&F&F&0&0&lSTRAW HAT");
         this.config.set("hats.dimmadome.color", "&#FFC42B");
         this.config.set("hats.dimmadome.itemsadder-id", "hats:dimmadome");
         this.config.set("hats.dimmadome.display-name", "&x&F&F&C&4&2&B&lDIMMADOME");
         this.config.set("config-version", 12);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 13) {
         this.config.set(
            "hat-lore",
            List.of(
               "&8Description",
               "",
               "%color%Information:",
               "%color%| &fClick to equip",
               "%color%| &fthis Cosmetic As Your Helmet",
               "",
               "%color%⚓ &fStatus: %status%",
               "",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nLEFT-CLICK&r &x&F&F&B&A&0&0To Equip",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nRIGHT-CLICK&r &x&F&F&B&A&0&0To Equip"
            )
         );
         this.config.set("hats.tophat.itemsadder-id", "hats:tophat");
         this.config.set("hats.tophat.material", "hats:tophat");
         this.config.set("hats.crown.itemsadder-id", "hats:crown");
         this.config.set("hats.crown.material", "hats:crown");
         this.config.set("hats.propeller_hat.itemsadder-id", "hats:propeller_hat");
         this.config.set("hats.propeller_hat.material", "hats:propeller_hat");
         this.config.set("hats.clown_mask.itemsadder-id", "hats:clown_mask");
         this.config.set("hats.clown_mask.material", "hats:clown_mask");
         this.config.set("hats.sstraw_hat.itemsadder-id", "hats:strawhat");
         this.config.set("hats.sstraw_hat.material", "hats:strawhat");
         this.config.set("hats.sstraw_hat.display-name", "&x&B&3&F&F&0&0&lSTRAW HAT");
         this.config.set("hats.dimmadome.itemsadder-id", "hats:dimmadome");
         this.config.set("hats.dimmadome.material", "hats:dimmadome");
         this.config.set("config-version", 13);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 14) {
         this.config.set(
            "hat-lore",
            List.of(
               "&8Description",
               "",
               "%color%Information:",
               "%color%| &fClick to equip",
               "%color%| &fthis Cosmetic As Your Helmet",
               "",
               "%color%⚓ &fStatus: %status%",
               "",
               "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nLEFT-CLICK&r &x&F&F&B&A&0&0To Equip"
            )
         );
         this.config.set("favourite-click-lore", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nRIGHT-CLICK&r &x&F&F&B&A&0&0To Favourite");
         this.config.set("unfavourite-click-lore", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nRIGHT-CLICK&r &x&F&F&B&A&0&0To Unfavourite");
         this.config.set("config-version", 14);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 16) {
         for (String id : List.of(
            "tophat", "crown", "propeller_hat", "clown_mask", "sstraw_hat", "dimmadome", "pharaoh_hat", "pharaon_hat"
         )) {
            this.config.set("hats." + id + ".hidden", true);
         }
         this.config.set("hats.pharaon_hat.itemsadder-id", "somehats:pharaon_hat");
         this.config.set("hats.pharaoh_hat.itemsadder-id", "somehats:pharaoh_hat");
         this.config.set("config-version", 16);
         flag = true;
      }

      int i = this.config.getInt("default-price", 800);

      for (HatCatalog.Seed hatcatalog$seed : HatCatalog.all()) {
         String s = "hats." + hatcatalog$seed.id();
         if (this.config.getConfigurationSection(s) == null) {
            this.config.set(s + ".itemsadder-id", hatcatalog$seed.itemsadderId());
            this.config.set(s + ".material", hatcatalog$seed.itemsadderId());
            this.config.set(s + ".display-name", hatcatalog$seed.displayName());
            this.config.set(s + ".permission", "sharded.wardrobe." + hatcatalog$seed.id());
            this.config.set(s + ".price", hatcatalog$seed.price());
            this.config.set(s + ".hidden", HatCatalog.isRemoved(hatcatalog$seed.id(), hatcatalog$seed.itemsadderId()));
            this.config.set(s + ".limited", false);
            flag = true;
         } else {
            if (!this.config.contains(s + ".itemsadder-id")) {
               this.config.set(s + ".itemsadder-id", hatcatalog$seed.itemsadderId());
               flag = true;
            }

            if (!this.config.contains(s + ".price")) {
               this.config.set(s + ".price", hatcatalog$seed.price() > 0 ? hatcatalog$seed.price() : i);
               flag = true;
            }

            if (!this.config.contains(s + ".hidden")) {
               this.config.set(s + ".hidden", false);
               flag = true;
            }

            if (!this.config.contains(s + ".limited")) {
               this.config.set(s + ".limited", false);
               flag = true;
            }
         }
         if (HatCatalog.isRemoved(hatcatalog$seed.id(), hatcatalog$seed.itemsadderId()) && !this.config.getBoolean(s + ".hidden", false)) {
            this.config.set(s + ".hidden", true);
            flag = true;
         }
      }

      if (flag) {
         this.saveConfigFile();
      }
   }

   private void loadHats() {
      this.hats.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("hats");
      if (configurationsection != null) {
         int i = this.config.getInt("default-price", 800);

         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null) {
               String s1 = s.toLowerCase(Locale.ROOT);
               String ia = configurationsection1.getString("itemsadder-id", configurationsection1.getString("material", "hats:" + s1));
               if (HatCatalog.isRemoved(s1, ia, configurationsection1.getString("material", ia))) {
                  continue;
               }
               this.hats
                  .put(
                     s1,
                     new WardrobeModule.HatOption(
                        s1,
                        configurationsection1.getString("permission", "sharded.wardrobe." + s1),
                        configurationsection1.getString("itemsadder-id", configurationsection1.getString("material", "hats:" + s1)),
                        configurationsection1.getString("material", configurationsection1.getString("itemsadder-id", "hats:" + s1)),
                        configurationsection1.getString("display-name", s),
                        configurationsection1.getString("color", HatCatalog.colorFor(s1)),
                        configurationsection1.getStringList("lore"),
                        configurationsection1.getBoolean("hidden", false),
                        configurationsection1.getBoolean("limited", false),
                        Math.max(1, configurationsection1.getInt("price", i))
                     )
                  );
            }
         }
      }
   }

   private void saveConfigFile() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
      }
   }

   public boolean isWardrobeHat(ItemStack stack) {
      return stack != null && !stack.getType().isAir() && stack.hasItemMeta()
         ? stack.getItemMeta().getPersistentDataContainer().has(this.hatKey, PersistentDataType.STRING)
         : false;
   }

   public boolean unlock(Player player, String hatId) {
      return this.unlock(player, hatId, true);
   }

   public boolean unlock(Player player, String hatId, boolean announce) {
      if (player == null) {
         return false;
      }
      return this.unlock(player.getUniqueId(), hatId, announce ? player : null);
   }

   public boolean unlock(UUID uuid, String hatId, Player announceTo) {
      WardrobeModule.HatOption hat = this.findHat(hatId);
      if (hat == null || uuid == null) {
         return false;
      }
      if (this.database != null) {
         this.database.unlock(uuid, hat.id());
      }
      Player online = announceTo != null ? announceTo : org.bukkit.Bukkit.getPlayer(uuid);
      if (online != null && this.plugin.luckPerms() != null) {
         this.plugin.luckPerms().runConsole("lp user " + online.getName() + " permission set sharded.wardrobe." + hat.id() + " true");
      }
      if (announceTo != null) {
         this.send(announceTo, "unlocked", new String[]{"%hat%", hat.displayName()});
      }
      return true;
   }

   public boolean isUnlocked(UUID uuid, String hatId) {
      WardrobeModule.HatOption hat = this.findHat(hatId);
      return hat != null && this.database != null && this.database.isUnlocked(uuid, hat.id());
   }

   public boolean ownsHat(UUID uuid, String hatId) {
      if (uuid == null || hatId == null) {
         return false;
      }
      WardrobeModule.HatOption hat = this.findHat(hatId);
      if (hat == null) {
         return this.database != null && this.database.isUnlocked(uuid, hatId.toLowerCase(Locale.ROOT));
      }
      if (this.database != null && this.database.isUnlocked(uuid, hat.id())) {
         return true;
      }
      Player player = Bukkit.getPlayer(uuid);
      return player != null && this.owns(player, hat);
   }

   public boolean owns(Player player, WardrobeModule.HatOption hat) {
      if (player == null || hat == null) {
         return false;
      }
      return player.hasPermission(hat.permission()) || this.database != null && this.database.isUnlocked(player.getUniqueId(), hat.id());
   }

   private String[] hatCompleteIds() {
      LinkedHashMap<String, Boolean> ids = new LinkedHashMap<>();
      for (WardrobeModule.HatOption hat : this.hats.values()) {
         ids.put(hat.id(), true);
         if (hat.itemsadderId() != null && !hat.itemsadderId().isBlank()) {
            ids.put(hat.itemsadderId(), true);
         }
      }
      return ids.keySet().toArray(String[]::new);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (command.getName().equalsIgnoreCase("hatshop")) {
         if (sender instanceof Player player1) {
            if (!player1.hasPermission("sharded.tokenshop.use") && !player1.hasPermission("tokenshop.use") && !player1.isOp()) {
               this.send(player1, "no-permission", new String[0]);
               return true;
            } else {
               player1.performCommand("itemshop");
               return true;
            }
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      } else if (this.isAdminSub(args)) {
         return this.handleAdmin(sender, args);
      } else if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.wardrobe.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else if (args.length <= 0 || !args[0].equalsIgnoreCase("remove") && !args[0].equalsIgnoreCase("off")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("limited")) {
               this.openGui(player, false, "limited", 0);
               return true;
            } else {
               this.openMenu(player);
               return true;
            }
         } else {
            this.unequip(player);
            this.send(player, "removed", new String[0]);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (command.getName().equalsIgnoreCase("hatshop")) {
         return List.of();
      } else if (args.length == 1) {
         List<String> list = new ArrayList<>(List.of("remove", "off", "limited"));
         if (this.isAdmin(sender)) {
            list.addAll(List.of("hide", "show", "delete", "removehat"));
         }

         return TabCompleteHelper.filter(args[0], list.toArray(String[]::new));
      } else if (args.length == 2 && this.isAdmin(sender) && args[0].equalsIgnoreCase("limited")) {
         return TabCompleteHelper.filter(args[1], "add", "remove");
      } else if (args.length != 2
         || !this.isAdmin(sender)
         || !args[0].equalsIgnoreCase("hide")
            && !args[0].equalsIgnoreCase("show")
            && !args[0].equalsIgnoreCase("delete")
            && !args[0].equalsIgnoreCase("removehat")) {
         return args.length == 3 && this.isAdmin(sender) && args[0].equalsIgnoreCase("limited")
            ? TabCompleteHelper.filter(args[2], this.hats.keySet().toArray(String[]::new))
            : List.of();
      } else {
         return TabCompleteHelper.filter(args[1], this.hatCompleteIds());
      }
   }

   public void openMenu(Player player) {
      this.openGui(player, false, "all", 0);
   }

   public void openShop(Player player) {
      this.openGui(player, true, "all", 0);
   }

   private void openGui(Player player, boolean shop, String tab, int page) {
      if (!"limited".equals(tab) && !"favourites".equals(tab)) {
         tab = "all";
      }

      List<WardrobeModule.HatOption> list = this.visibleHats(player, tab);
      int i = CONTENT.length;
      int j = Math.max(0, (list.size() + i - 1) / i - 1);
      page = Math.max(0, Math.min(page, j));
      WardrobeModule.MenuHolder wardrobemodule$menuholder = new WardrobeModule.MenuHolder(shop, tab, page);
      String s = this.guiTitle(shop, tab);
      Inventory inventory = this.plugin.getServer().createInventory(wardrobemodule$menuholder, 54, Text.c(s));
      TrackedInventories.track(inventory, wardrobemodule$menuholder);
      Material material = Material.matchMaterial(this.config.getString("filler-material", "BLACK_STAINED_GLASS_PANE"));
      if (material == null) {
         material = Material.BLACK_STAINED_GLASS_PANE;
      }

      ItemStack itemstack = new ItemBuilder(material).name(" ").hideAll().build();

      for (int k = 0; k < inventory.getSize(); k++) {
         inventory.setItem(k, itemstack.clone());
      }

      Map<String, String> map = this.equipPlaceholders(player);
      int l = page * i;

      for (int i1 = 0; i1 < i && l + i1 < list.size(); i1++) {
         WardrobeModule.HatOption wardrobemodule$hatoption = list.get(l + i1);
         inventory.setItem(CONTENT[i1], this.guiItem(player, wardrobemodule$hatoption, shop, map));
         wardrobemodule$menuholder.actions.put(CONTENT[i1], (shop ? "buy:" : "equip:") + wardrobemodule$hatoption.id());
      }

      if (page > 0) {
         inventory.setItem(45, this.navButton("previous"));
         wardrobemodule$menuholder.actions.put(45, "page:" + (page - 1));
      }

      if (page < j) {
         inventory.setItem(53, this.navButton("next"));
         wardrobemodule$menuholder.actions.put(53, "page:" + (page + 1));
      }

      int j1 = this.config.getInt("extra.slot", 49);
      if (!"limited".equals(tab) && !"favourites".equals(tab)) {
         inventory.setItem(
            j1,
            this.styledItem(
               this.config.getString("extra.material", "CLOCK"),
               this.config.getString("extra.name", "&#FFBA00&lLIMITED HATS"),
               this.config.getStringList("extra.lore"),
               Material.CLOCK
            )
         );
         wardrobemodule$menuholder.actions.put(j1, "tab:limited");
      } else {
         inventory.setItem(j1, this.navButton("back"));
         wardrobemodule$menuholder.actions.put(j1, "tab:all");
      }

      inventory.setItem(
         this.config.getInt("favourites.slot", 51),
         this.styledItem(
            this.config.getString("favourites.material", "CHEST_MINECART"),
            this.config.getString("favourites.name", "&#FFBA00&lFAVOURITES"),
            this.config.getStringList("favourites.lore"),
            Material.NETHER_STAR
         )
      );
      wardrobemodule$menuholder.actions.put(this.config.getInt("favourites.slot", 51), "tab:favourites");
      if (shop) {
         inventory.setItem(this.config.getInt("remove.slot", 4), this.navButton("back"));
         wardrobemodule$menuholder.actions.put(this.config.getInt("remove.slot", 4), "back");
      } else {
         inventory.setItem(
            this.config.getInt("remove.slot", 4),
            this.styledItem(
               this.config.getString("remove.material", "BARRIER"),
               this.config.getString("remove.display-name", "&#FF0000&lREMOVE HAT"),
               this.config.getStringList("remove.lore"),
               Material.BARRIER
            )
         );
         wardrobemodule$menuholder.actions.put(this.config.getInt("remove.slot", 4), "remove");
      }

      player.openInventory(inventory);
   }

   private String guiTitle(boolean shop, String tab) {
      if (shop) {
         if ("limited".equals(tab)) {
            return this.config.getString("shop-limited-title", "Token Shop | Limited");
         } else {
            return "favourites".equals(tab)
               ? this.config.getString("shop-favourites-title", "Token Shop | Favourites")
               : this.config.getString("shop-title", "Token Shop | Cosmetics");
         }
      } else if ("limited".equals(tab)) {
         return this.config.getString("limited-title", "Wardrobe | Limited");
      } else {
         return "favourites".equals(tab) ? this.config.getString("favourites-title", "Wardrobe | Favourites") : this.config.getString("gui-title", "Wardrobe");
      }
   }

   public void handleMenuClick(Player player, int slot) {
      this.handleMenuClick(player, slot, Boolean.TRUE.equals(this.rightClicks.remove(player.getUniqueId())));
   }

   public void handleMenuClick(Player player, int slot, boolean rightClick) {
      this.rightClicks.remove(player.getUniqueId());
      if (!this.clickGuard.add(player.getUniqueId())) {
         return;
      }
      Bukkit.getScheduler().runTask(this.plugin, () -> this.clickGuard.remove(player.getUniqueId()));
      Inventory inventory = player.getOpenInventory().getTopInventory();
      WardrobeModule.MenuHolder wardrobemodule$menuholder = TrackedInventories.lookup(inventory, WardrobeModule.MenuHolder.class);
      if (wardrobemodule$menuholder != null) {
         String s = wardrobemodule$menuholder.actions.get(slot);
         if (s != null) {
            if (s.startsWith("page:")) {
               int i = Integer.parseInt(s.substring(5));
               this.openGui(player, wardrobemodule$menuholder.shop, wardrobemodule$menuholder.tab, i);
            } else if (s.equals("tab:limited")) {
               this.openGui(player, wardrobemodule$menuholder.shop, "limited", 0);
            } else if (s.equals("tab:all")) {
               this.openGui(player, wardrobemodule$menuholder.shop, "all", 0);
            } else if (s.equals("tab:favourites")) {
               this.openGui(player, wardrobemodule$menuholder.shop, "favourites", 0);
            } else if (s.equals("back")) {
               player.closeInventory();
               this.plugin.gui().open(player, "mainmenu");
            } else if (s.equals("remove")) {
               this.unequip(player);
               this.send(player, "removed", new String[0]);
               this.openGui(player, false, wardrobemodule$menuholder.tab, wardrobemodule$menuholder.page);
            } else {
               if (s.startsWith("equip:") || s.startsWith("buy:")) {
                  boolean flag = s.startsWith("buy:");
                  WardrobeModule.HatOption wardrobemodule$hatoption = this.findHat(s.substring(flag ? 4 : 6));
                  if (wardrobemodule$hatoption == null) {
                     return;
                  }

                  if (rightClick) {
                     this.toggleFavourite(player, wardrobemodule$hatoption);
                     this.openGui(player, wardrobemodule$menuholder.shop, wardrobemodule$menuholder.tab, wardrobemodule$menuholder.page);
                     return;
                  }

                  if (flag) {
                     this.buyHat(player, wardrobemodule$hatoption);
                     this.openGui(player, true, wardrobemodule$menuholder.tab, wardrobemodule$menuholder.page);
                     return;
                  }

                  if (!this.owns(player, wardrobemodule$hatoption)) {
                     this.send(player, "not-owned", new String[]{"%hat%", wardrobemodule$hatoption.displayName()});
                     return;
                  }

                  if (this.isEquipped(player, wardrobemodule$hatoption)) {
                     this.unequip(player);
                     this.send(player, "removed", new String[0]);
                     this.openGui(player, false, wardrobemodule$menuholder.tab, wardrobemodule$menuholder.page);
                     return;
                  }

                  if (!this.equip(player, wardrobemodule$hatoption)) {
                     return;
                  }

                  this.send(player, "equipped", new String[]{"%hat%", this.coloredName(wardrobemodule$hatoption, Map.of())});
                  this.openGui(player, false, wardrobemodule$menuholder.tab, wardrobemodule$menuholder.page);
               }
            }
         }
      }
   }

   public Map<String, String> equipPlaceholders(Player player) {
      LinkedHashMap<String, String> linkedhashmap = new LinkedHashMap<>();
      String s = this.config.getString("placeholders.owned-yes", "&#9FFF00&nYes");
      String s1 = this.config.getString("placeholders.owned-no", "&#FF2727&nNo");

      for (WardrobeModule.HatOption wardrobemodule$hatoption : this.hats.values()) {
         linkedhashmap.put("wardrobe_owned_" + wardrobemodule$hatoption.id(), this.owns(player, wardrobemodule$hatoption) ? s : s1);
      }

      String s2 = this.database == null ? "" : this.database.getEquipped(player.getUniqueId());
      linkedhashmap.put("equipped_hat", s2 != null && !s2.isBlank() ? s2 : this.config.getString("placeholders.none", "&7None"));
      return linkedhashmap;
   }

   private boolean isEquipped(Player player, WardrobeModule.HatOption hat) {
      if (this.database == null) {
         return false;
      } else {
         String s = this.database.getEquipped(player.getUniqueId());
         return s != null && s.equalsIgnoreCase(hat.id());
      }
   }

   private void trackWearer(UUID uuid) {
      this.hatWearers.add(uuid);
   }

   private void untrackWearer(UUID uuid) {
      this.hatWearers.remove(uuid);
   }

   private void syncWearerState(Player player) {
      if (this.isWardrobeHat(player.getInventory().getHelmet())) {
         this.trackWearer(player.getUniqueId());
      } else if (this.database != null) {
         String s = this.database.getEquipped(player.getUniqueId());
         if (s != null && !s.isBlank()) {
            this.scheduleReequip(player);
         }
      }
   }

   private void scheduleReequip(Player player) {
      if (this.database != null) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.reequipIfNeeded(player), 5L);
      }
   }

   private void reequipIfNeeded(Player player) {
      if (player.isOnline()) {
         String s = this.database.getEquipped(player.getUniqueId());
         if (s != null && !s.isBlank()) {
            WardrobeModule.HatOption wardrobemodule$hatoption = this.hats.get(s);
            if (wardrobemodule$hatoption != null && this.owns(player, wardrobemodule$hatoption)) {
               ItemStack itemstack = player.getInventory().getHelmet();
               if (this.isWardrobeHat(itemstack)) {
                  this.trackWearer(player.getUniqueId());
               } else {
                  this.equipSilent(player, wardrobemodule$hatoption, false);
               }
            }
         }
      }
   }

   public boolean equip(Player player, WardrobeModule.HatOption hat) {
      if (!this.equipSilent(player, hat, true)) {
         return false;
      } else {
         if (this.database != null) {
            this.database.setEquipped(player.getUniqueId(), hat.id());
         }

         return true;
      }
   }

   private boolean equipSilent(Player player, WardrobeModule.HatOption hat, boolean storeHelmet) {
      ItemStack itemstack = this.buildHatItem(hat);
      if (itemstack == null) {
         if (ItemsAdderHook.isAvailable() && !ItemsAdderHook.isRegistryReady()) {
            this.send(player, "still-loading", new String[0]);
            return false;
         }

         itemstack = this.buildFallbackHat(hat);
      }

      if (itemstack == null) {
         this.send(player, "item-missing", new String[]{"%hat%", hat.displayName()});
         return false;
      } else {
         PlayerInventory playerinventory = player.getInventory();
         ItemStack itemstack1 = playerinventory.getHelmet();
         if (this.isWardrobeHat(itemstack1)) {
            playerinventory.setHelmet(null);
         } else if (itemstack1 != null && !itemstack1.getType().isAir()) {
            if (storeHelmet && this.database != null && this.database.loadHelmet(player.getUniqueId()) == null) {
               this.database.saveHelmet(player.getUniqueId(), itemstack1.clone());
            }

            playerinventory.setHelmet(null);
         }

         playerinventory.setHelmet(itemstack);
         this.trackWearer(player.getUniqueId());
         return true;
      }
   }

   public void clearCosmeticHat(Player player) {
      this.unequip(player);
   }

   private void unequip(Player player) {
      PlayerInventory playerinventory = player.getInventory();
      if (this.isWardrobeHat(playerinventory.getHelmet())) {
         playerinventory.setHelmet(null);
      }

      this.removeWardrobeHatsFromInventory(player);
      this.untrackWearer(player.getUniqueId());
      if (this.database != null) {
         this.database.setEquipped(player.getUniqueId(), "");
         ItemStack itemstack = this.database.loadHelmet(player.getUniqueId());
         if (itemstack != null && !itemstack.getType().isAir()) {
            ItemStack itemstack1 = playerinventory.getHelmet();
            if (itemstack1 != null && !itemstack1.getType().isAir()) {
               Map<Integer, ItemStack> map = playerinventory.addItem(new ItemStack[]{itemstack});
               map.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            } else {
               playerinventory.setHelmet(itemstack);
            }
         }

         this.database.clearHelmet(player.getUniqueId());
      }
   }

   private void removeWardrobeHatsFromInventory(Player player) {
      PlayerInventory playerinventory = player.getInventory();

      for (int i = 0; i < playerinventory.getSize(); i++) {
         if (this.isWardrobeHat(playerinventory.getItem(i))) {
            playerinventory.setItem(i, null);
         }
      }

      ItemStack itemstack = playerinventory.getItemInOffHand();
      if (this.isWardrobeHat(itemstack)) {
         playerinventory.setItemInOffHand(null);
      }
   }

   private ItemStack resolveDisplayItem(WardrobeModule.HatOption hat) {
      ItemStack itemstack = this.resolveItemsAdder(hat);
      return itemstack != null && !this.isPaperFallback(itemstack)
         ? itemstack.clone()
         : new ItemBuilder(Material.LEATHER_HELMET).name(hat.displayName()).hideAll().build();
   }

   private ItemStack resolveItemsAdder(WardrobeModule.HatOption hat) {
      LinkedHashMap<String, Boolean> linkedhashmap = new LinkedHashMap<>();

      for (String s : List.of(hat.itemsadderId(), hat.material(), "HATS:" + hat.id().toUpperCase(Locale.ROOT), hat.id())) {
         if (s != null && !s.isBlank()) {
            linkedhashmap.put(s, Boolean.TRUE);
            ItemStack itemstack = ItemsAdderHook.resolveCustom(s);
            if (itemstack != null && !this.isPaperFallback(itemstack)) {
               return itemstack;
            }
         }
      }

      for (String s1 : ItemsAdderHook.hatAliases(hat.id())) {
         if (linkedhashmap.put(s1, Boolean.TRUE) == null) {
            ItemStack itemstack1 = ItemsAdderHook.resolveCustom(s1);
            if (itemstack1 != null && !this.isPaperFallback(itemstack1)) {
               return itemstack1;
            }
         }
      }

      return ItemsAdderHook.matchRegistry(hat.id(), hat.itemsadderId(), hat.material(), "TOP Hat");
   }

   private boolean isPaperFallback(ItemStack stack) {
      return stack == null || stack.getType().isAir() || stack.getType() == Material.PAPER;
   }

   private ItemStack buildHatItem(WardrobeModule.HatOption hat) {
      ItemStack itemstack = this.resolveItemsAdder(hat);
      return itemstack != null && !this.isPaperFallback(itemstack) ? this.decorateHat(hat, itemstack.clone()) : null;
   }

   private ItemStack buildFallbackHat(WardrobeModule.HatOption hat) {
      return this.decorateHat(hat, new ItemBuilder(Material.LEATHER_HELMET).name(hat.displayName()).hideAll().build());
   }

   private ItemStack decorateHat(WardrobeModule.HatOption hat, ItemStack stack) {
      if (stack != null && !stack.getType().isAir()) {
         int i = this.config.getInt("enchantments.protection", 4);
         int j = this.config.getInt("enchantments.unbreaking", 3);
         double d0 = this.config.getDouble("attributes.armor", 3.0);
         double d1 = this.config.getDouble("attributes.armor-toughness", 0.0);
         ItemMeta itemmeta = stack.getItemMeta();
         if (itemmeta != null) {
            itemmeta.addEnchant(Enchantment.PROTECTION, i, true);
            itemmeta.addEnchant(Enchantment.UNBREAKING, j, true);
            itemmeta.addEnchant(Enchantment.MENDING, 1, true);
            itemmeta.setUnbreakable(false);
            NamespacedKey namespacedkey = new NamespacedKey(this.plugin, "wardrobe_armor_" + hat.id());
            itemmeta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(namespacedkey, d0, Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD));
            if (d1 > 0.0) {
               NamespacedKey namespacedkey1 = new NamespacedKey(this.plugin, "wardrobe_toughness_" + hat.id());
               itemmeta.addAttributeModifier(
                  Attribute.ARMOR_TOUGHNESS, new AttributeModifier(namespacedkey1, d1, Operation.ADD_NUMBER, EquipmentSlotGroup.HEAD)
               );
            }

            itemmeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES});
            itemmeta.getPersistentDataContainer().set(this.hatKey, PersistentDataType.STRING, hat.id());
            if (itemmeta.displayName() != null && itemmeta.itemName() != null) {
               String custom = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(itemmeta.displayName()).trim();
               String itemName = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(itemmeta.itemName()).trim();
               if (!custom.isEmpty() && custom.equalsIgnoreCase(itemName)) {
                  itemmeta.itemName(null);
               }
            }
            stack.setItemMeta(itemmeta);
         }

         return stack;
      } else {
         return null;
      }
   }

   private boolean isAdmin(CommandSender sender) {
      return sender.isOp() || sender.hasPermission("sharded.wardrobe.admin");
   }

   private boolean isAdminSub(String[] args) {
      if (args.length == 0) {
         return false;
      } else {
         return !args[0].equalsIgnoreCase("hide")
            && !args[0].equalsIgnoreCase("show")
            && !args[0].equalsIgnoreCase("delete")
            && !args[0].equalsIgnoreCase("removehat")
            ? args[0].equalsIgnoreCase("limited") && args.length >= 3
            : true;
      }
   }

   private boolean handleAdmin(CommandSender sender, String[] args) {
      if (!this.isAdmin(sender)) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         if ((s.equals("hide") || s.equals("show") || s.equals("delete") || s.equals("removehat")) && args.length >= 2) {
            WardrobeModule.HatOption wardrobemodule$hatoption1 = this.findHat(args[1]);
            if (wardrobemodule$hatoption1 == null) {
               this.send(sender, "unknown-hat", new String[]{"%hat%", args[1]});
               return true;
            } else {
               boolean hide = !s.equals("show");
               this.config.set("hats." + wardrobemodule$hatoption1.id() + ".hidden", hide);
               this.saveConfigFile();
               this.loadHats();
               var itemshop = this.plugin.modules().get(com.sharded.core.modules.itemshop.ItemShopModule.class);
               if (itemshop != null) {
                  itemshop.setHatListed(wardrobemodule$hatoption1.id(), !hide);
                  itemshop.setHatListed(wardrobemodule$hatoption1.itemsadderId(), !hide);
               }
               this.send(sender, hide ? "hidden" : "shown", new String[]{"%hat%", wardrobemodule$hatoption1.displayName()});
               return true;
            }
         } else if (s.equals("limited") && args.length >= 3) {
            WardrobeModule.HatOption wardrobemodule$hatoption = this.findHat(args[2]);
            if (wardrobemodule$hatoption == null) {
               this.send(sender, "unknown-hat", new String[]{"%hat%", args[2]});
               return true;
            } else {
               boolean flag = args[1].equalsIgnoreCase("add");
               this.config.set("hats." + wardrobemodule$hatoption.id() + ".limited", flag);
               this.saveConfigFile();
               this.loadHats();
               var itemshop = this.plugin.modules().get(com.sharded.core.modules.itemshop.ItemShopModule.class);
               if (itemshop != null) {
                  itemshop.refreshShops();
               }
               this.send(sender, flag ? "limited-added" : "limited-removed", new String[]{"%hat%", wardrobemodule$hatoption.displayName()});
               return true;
            }
         } else {
            this.send(sender, "admin-usage", new String[0]);
            return true;
         }
      }
   }

   public WardrobeModule.HatOption findHat(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.toLowerCase(Locale.ROOT);
         WardrobeModule.HatOption wardrobemodule$hatoption = this.hats.get(s);
         if (wardrobemodule$hatoption != null) {
            return wardrobemodule$hatoption;
         } else {
            if (s.contains(":")) {
               s = s.substring(s.lastIndexOf(58) + 1);
               wardrobemodule$hatoption = this.hats.get(s);
               if (wardrobemodule$hatoption != null) {
                  return wardrobemodule$hatoption;
               }

               wardrobemodule$hatoption = this.hats.get("somehats_" + s);
               if (wardrobemodule$hatoption != null) {
                  return wardrobemodule$hatoption;
               }
            }

            for (WardrobeModule.HatOption wardrobemodule$hatoption1 : this.hats.values()) {
               if (wardrobemodule$hatoption1.itemsadderId().equalsIgnoreCase(raw) || wardrobemodule$hatoption1.material().equalsIgnoreCase(raw)) {
                  return wardrobemodule$hatoption1;
               }

               String s1 = wardrobemodule$hatoption1.itemsadderId();
               int i = s1.lastIndexOf(58);
               if (i >= 0 && s1.substring(i + 1).equalsIgnoreCase(s)) {
                  return wardrobemodule$hatoption1;
               }
            }

            return null;
         }
      } else {
         return null;
      }
   }

   public List<String> limitedHatIds() {
      List<String> ids = new ArrayList<>();
      for (WardrobeModule.HatOption hat : this.hats.values()) {
         if (hat.limited() && !hat.hidden()) {
            ids.add(hat.id());
         }
      }
      return ids;
   }

   private List<WardrobeModule.HatOption> visibleHats(Player player, String tab) {
      ArrayList<WardrobeModule.HatOption> arraylist = new ArrayList<>();

      for (WardrobeModule.HatOption wardrobemodule$hatoption : this.hats.values()) {
         if (!wardrobemodule$hatoption.hidden()) {
            if ("favourites".equals(tab)) {
               if (this.database != null && this.database.isFavourite(player.getUniqueId(), wardrobemodule$hatoption.id())) {
                  arraylist.add(wardrobemodule$hatoption);
               }
            } else if ("limited".equals(tab) == wardrobemodule$hatoption.limited()) {
               arraylist.add(wardrobemodule$hatoption);
            }
         }
      }

      return arraylist;
   }

   private void toggleFavourite(Player player, WardrobeModule.HatOption hat) {
      if (this.database != null) {
         boolean flag = this.database.toggleFavourite(player.getUniqueId(), hat.id());
         this.send(player, flag ? "favourited" : "unfavourited", new String[]{"%hat%", hat.displayName()});
      }
   }

   private ItemStack guiItem(Player player, WardrobeModule.HatOption hat, boolean shop, Map<String, String> ph) {
      ItemStack itemstack = this.resolveDisplayItem(hat);
      String s = hat.color() != null && !hat.color().isBlank() ? hat.color() : HatCatalog.colorFor(hat.id());
      String s1 = this.owns(player, hat)
         ? this.config.getString("status-owned", "&#94FF00&l&nOWNED")
         : this.config.getString("status-locked", "&#FF0000&l&nLOCKED");
      List<String> list = this.config.getStringList("hat-lore");
      if (list.isEmpty()) {
         list = List.of(
            "&8Description",
            "",
            "%color%Information:",
            "%color%| &fClick to equip",
            "%color%| &fthis Cosmetic As Your Helmet",
            "",
            "%color%⚓ &fStatus: %status%",
            "",
            "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nLEFT-CLICK&r &x&F&F&B&A&0&0To Equip"
         );
      }

      List<String> list1 = new ArrayList<>();

      for (String s2 : list) {
         if (s2.toLowerCase(Locale.ROOT).contains("right-click") && s2.toLowerCase(Locale.ROOT).contains("equip")) {
            continue;
         }
         list1.add(s2.replace("%color%", s).replace("%status%", s1).replace(":anchor:", "⚓"));
      }

      if (hat.limited()) {
         list1.add(this.config.getString("limited-lore", "&e&lLimited hat will be gone soon"));
      }

      boolean favourited = this.database != null && this.database.isFavourite(player.getUniqueId(), hat.id());
      if (favourited) {
         list1.add(this.config.getString("favourite-lore", "&#FF55FF⚓ &fFavourited"));
         list1.add(this.config.getString("unfavourite-click-lore", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nRIGHT-CLICK&r &x&F&F&B&A&0&0To Unfavourite"));
      } else {
         list1.add(this.config.getString("favourite-click-lore", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nRIGHT-CLICK&r &x&F&F&B&A&0&0To Favourite"));
      }

      if (shop) {
         list1.add("");
         list1.add(s + "⚓ &fCost: &e" + hat.price() + " Tokens");
      }

      return new ItemBuilder(itemstack).name(this.coloredName(hat, ph)).lore(this.apply(list1, ph)).hideAll().build();
   }

   private ItemStack navButton(String type) {
      if (this.plugin.guiNavigation() != null) {
         ItemStack itemstack = this.plugin.guiNavigation().build(type);
         if (itemstack != null && !itemstack.getType().isAir()) {
            return itemstack;
         }
      }

      if ("previous".equals(type)) {
         return this.styledItem("RED_DYE", "&#FF005D&lPREVIOUS", List.of("&7Previous page"), Material.RED_DYE);
      } else {
         return "next".equals(type)
            ? this.styledItem("LIME_DYE", "&#94FF00&lNEXT", List.of("&7Next page"), Material.LIME_DYE)
            : this.styledItem("BARRIER", "&#FF0000&lBACK", List.of("&7Go back"), Material.BARRIER);
      }
   }

   private ItemStack styledItem(String material, String name, List<String> lore, Material fallback) {
      Material materialx = Material.matchMaterial(material == null ? "" : material.toUpperCase(Locale.ROOT));
      if (materialx == null || materialx.isAir()) {
         materialx = fallback;
      }

      return new ItemBuilder(materialx).name(name).lore(lore == null ? List.of() : lore).hideAll().build();
   }

   private void buyHat(Player player, WardrobeModule.HatOption hat) {
      if (this.owns(player, hat)) {
         this.send(player, "already-owned", new String[]{"%hat%", hat.displayName()});
      } else {
         TokenService tokenservice = this.plugin.modules().tokens();
         if (tokenservice == null) {
            this.send(player, "shop-unavailable", new String[0]);
         } else {
            long i = tokenservice.getBalance(player.getUniqueId());
            if (i >= (long)hat.price() && tokenservice.take(player.getUniqueId(), (long)hat.price())) {
               if (this.database != null) {
                  this.database.unlock(player.getUniqueId(), hat.id());
               }

               if (this.plugin.luckPerms() != null) {
                  this.plugin.luckPerms().runConsole("lp user " + player.getName() + " permission set sharded.wardrobe." + hat.id() + " true");
               }

               this.send(player, "purchased", new String[]{"%hat%", hat.displayName(), "%price%", String.valueOf(hat.price())});
            } else {
               this.send(
                  player, "not-enough-tokens", new String[]{"%hat%", hat.displayName(), "%missing%", String.valueOf(Math.max(0L, (long)hat.price() - i))}
               );
            }
         }
      }
   }

   private List<String> apply(List<String> lines, Map<String, String> ph) {
      ArrayList<String> arraylist = new ArrayList<>();

      for (String s : lines) {
         arraylist.add(this.apply(s, ph));
      }

      return arraylist;
   }

   private String apply(String line, Map<String, String> ph) {
      String s = line;

      for (Entry<String, String> entry : ph.entrySet()) {
         s = s.replace("%" + entry.getKey() + "%", entry.getValue());
      }

      return s;
   }

   private String coloredName(WardrobeModule.HatOption hat, Map<String, String> ph) {
      String s = this.apply(hat.displayName(), ph);
      if (!s.contains("&#") && !s.contains("&x&")) {
         String s1 = hat.color() != null && !hat.color().isBlank() ? hat.color() : HatCatalog.colorFor(hat.id());
         return s1 + "&l" + s.replace("&f", "").replace("&l", "");
      } else {
         return s;
      }
   }

   private final class HatGuard implements Listener {
      @EventHandler(
         priority = EventPriority.HIGH,
         ignoreCancelled = true
      )
      public void onDrop(PlayerDropItemEvent event) {
         if (WardrobeModule.this.isWardrobeHat(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
         }
      }

      @EventHandler(
         priority = EventPriority.HIGH,
         ignoreCancelled = true
      )
      public void onHatMove(InventoryClickEvent event) {
         if (event.getWhoClicked() instanceof Player player) {
            if (WardrobeModule.this.isWardrobeHat(event.getCurrentItem()) || WardrobeModule.this.isWardrobeHat(event.getCursor())) {
               if (TrackedInventories.lookup(event.getView().getTopInventory(), WardrobeModule.MenuHolder.class) == null) {
                  event.setCancelled(true);
               }
            } else if (event.getClick() == ClickType.NUMBER_KEY) {
               ItemStack itemstack = player.getInventory().getItem(event.getHotbarButton());
               if (WardrobeModule.this.isWardrobeHat(itemstack)) {
                  event.setCancelled(true);
               }
            } else if (event.getSlotType() == SlotType.ARMOR && event.getRawSlot() == 39) {
               if (WardrobeModule.this.isWardrobeHat(player.getInventory().getHelmet())) {
                  event.setCancelled(true);
               }
            } else if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && WardrobeModule.this.isWardrobeHat(event.getCurrentItem())) {
               event.setCancelled(true);
            }
         }
      }

      @EventHandler(
         priority = EventPriority.HIGH,
         ignoreCancelled = true
      )
      public void onHatDrag(InventoryDragEvent event) {
         if (event.getWhoClicked() instanceof Player) {
            for (ItemStack itemstack : event.getNewItems().values()) {
               if (WardrobeModule.this.isWardrobeHat(itemstack)) {
                  event.setCancelled(true);
                  return;
               }
            }

            if (WardrobeModule.this.isWardrobeHat(event.getOldCursor())) {
               event.setCancelled(true);
            }
         }
      }

      @EventHandler(
         priority = EventPriority.HIGHEST
      )
      public void onDeath(PlayerDeathEvent event) {
         Player player = event.getEntity();
         event.getDrops().removeIf(WardrobeModule.this::isWardrobeHat);
         PlayerInventory playerinventory = player.getInventory();

         for (int i = 0; i < playerinventory.getSize(); i++) {
            if (WardrobeModule.this.isWardrobeHat(playerinventory.getItem(i))) {
               playerinventory.setItem(i, null);
            }
         }

         if (WardrobeModule.this.isWardrobeHat(playerinventory.getHelmet())) {
            playerinventory.setHelmet(null);
         }

         ItemStack itemstack = playerinventory.getItemInOffHand();
         if (WardrobeModule.this.isWardrobeHat(itemstack)) {
            playerinventory.setItemInOffHand(null);
         }

         WardrobeModule.this.untrackWearer(player.getUniqueId());
      }

      @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
      public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
         if (event.getEntity() instanceof Player && WardrobeModule.this.isWardrobeHat(event.getItem().getItemStack())) {
            event.setCancelled(true);
            event.getItem().remove();
         }
      }
   }

   private static record HatOption(
      String id,
      String permission,
      String itemsadderId,
      String material,
      String displayName,
      String color,
      List<String> lore,
      boolean hidden,
      boolean limited,
      int price
   ) {
   }

   private final class LifecycleListener implements Listener {
      @EventHandler
      public void onJoin(PlayerJoinEvent event) {
         WardrobeModule.this.plugin
            .getServer()
            .getScheduler()
            .runTaskLater(WardrobeModule.this.plugin, () -> WardrobeModule.this.syncWearerState(event.getPlayer()), 1L);
      }

      @EventHandler(
         priority = EventPriority.MONITOR
      )
      public void onRespawn(PlayerRespawnEvent event) {
         WardrobeModule.this.scheduleReequip(event.getPlayer());
      }

      @EventHandler
      public void onQuit(PlayerQuitEvent event) {
         WardrobeModule.this.untrackWearer(event.getPlayer().getUniqueId());
         WardrobeModule.this.rightClicks.remove(event.getPlayer().getUniqueId());
      }

      @EventHandler(
         priority = EventPriority.LOWEST
      )
      public void onMenuClick(InventoryClickEvent event) {
         if (event.getWhoClicked() instanceof Player player) {
            if (TrackedInventories.lookup(event.getView().getTopInventory(), WardrobeModule.MenuHolder.class) != null) {
               WardrobeModule.this.rightClicks.put(player.getUniqueId(), event.isRightClick());
            }
         }
      }
   }

   public static final class MenuHolder implements InventoryHolder {
      private final boolean shop;
      private final String tab;
      private final int page;
      private final Map<Integer, String> actions = new LinkedHashMap<>();

      private MenuHolder(boolean shop, String tab, int page) {
         this.shop = shop;
         this.tab = tab;
         this.page = page;
      }

      public Inventory getInventory() {
         return null;
      }
   }
}

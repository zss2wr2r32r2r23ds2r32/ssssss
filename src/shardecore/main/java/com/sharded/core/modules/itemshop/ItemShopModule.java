package com.sharded.core.modules.itemshop;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.modules.wardrobe.HatCatalog;
import com.sharded.core.modules.wardrobe.WardrobeModule;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;

public final class ItemShopModule extends Module implements CommandExecutor, TabCompleter {
   private final ItemShopCatalog catalog = new ItemShopCatalog();
   private ItemShopDatabase database;
   private ItemShopRotation rotation;
   private HatDisplayManager hats;
   private ItemShopGuis guis;
   private YamlConfiguration shop;
   private YamlConfiguration tokens;
   private YamlConfiguration raritiesYaml;
   private YamlConfiguration hatsYaml;
   private YamlConfiguration tagsYaml;
   private File hatsFile;
   private File tagsFile;
   private final Map<UUID, Set<String>> ownedHats = new ConcurrentHashMap<>();
   private final Map<UUID, Set<String>> ownedTags = new ConcurrentHashMap<>();
   private final Map<UUID, String> equippedHat = new ConcurrentHashMap<>();
   private final Map<UUID, String> equippedTag = new ConcurrentHashMap<>();
   private BukkitTask ticker;
   private BukkitTask expiry;

   public ItemShopModule(ShardedCore plugin) {
      super(plugin, "itemshop");
   }

   @Override
   protected void onEnable() {
      try {
         this.migrateOldFolder();
         this.database = new ItemShopDatabase(this.plugin, this.moduleFolder());
      } catch (Exception ex) {
         this.plugin.getLogger().severe("[itemshop] Could not open database: " + ex.getMessage());
         return;
      }
      this.hats = new HatDisplayManager(this.plugin);
      this.loadYamls();
      this.catalog.load(this.raritiesYaml, this.hatsYaml, this.tagsYaml);
      this.importLiveTags();
      this.rotation = new ItemShopRotation(this.catalog, this.database);
      this.guis = new ItemShopGuis(this);
      this.async(() -> {
         Map<UUID, Set<String>> hatsOwned = this.database.loadOwned("hat");
         Map<UUID, Set<String>> tagsOwned = this.database.loadOwned("tag");
         Map<UUID, ItemShopDatabase.Equipped> equipped = this.database.loadAllEquipped();
         this.sync(() -> {
            this.ownedHats.clear();
            this.ownedHats.putAll(hatsOwned);
            this.ownedTags.clear();
            this.ownedTags.putAll(tagsOwned);
            this.equippedHat.clear();
            this.equippedTag.clear();
            equipped.forEach((uuid, eq) -> {
               if (eq.hatId() != null) {
                  this.equippedHat.put(uuid, eq.hatId());
               }
               if (eq.tagId() != null) {
                  this.equippedTag.put(uuid, eq.tagId());
               }
            });
            this.async(() -> {
               this.rotation.loadOrRoll(this.shop, false);
               this.sync(() -> {
                  this.ensureLimitedCurrent();
                  this.startTasks();
                  for (Player player : Bukkit.getOnlinePlayers()) {
                     this.applyVisuals(player);
                  }
               });
            });
         });
      });
      this.registerCommand("itemshop", this);
   }

   @Override
   public void reload() {
      super.reload();
      this.loadYamls();
      this.catalog.load(this.raritiesYaml, this.hatsYaml, this.tagsYaml);
      this.importLiveTags();
      this.async(() -> this.rotation.loadOrRoll(this.shop, false));
   }

   @Override
   protected void onDisable() {
      if (this.ticker != null) {
         this.ticker.cancel();
      }
      if (this.expiry != null) {
         this.expiry.cancel();
      }
      if (this.hats != null) {
         this.hats.removeAll();
      }
      if (this.database != null) {
         this.database.close();
      }
   }

   private void migrateOldFolder() {
      File neu = this.moduleFolder();
      File old = new File(this.plugin.getDataFolder(), "modules/tokens/itemshop");
      if (!old.exists() || old.getAbsolutePath().equals(neu.getAbsolutePath())) {
         return;
      }
      File oldDb = new File(old, "itemshop.db");
      File newDb = new File(neu, "itemshop.db");
      if (oldDb.exists() && !newDb.exists()) {
         try {
            java.nio.file.Files.copy(oldDb.toPath(), newDb.toPath());
         } catch (Exception ignored) {
         }
      }
   }

   private void importLiveTags() {
      File tagsFileLive = new File(this.plugin.getDataFolder(), "modules/tags/config.yml");
      YamlConfiguration liveTags = tagsFileLive.exists()
         ? YamlConfiguration.loadConfiguration(tagsFileLive)
         : ConfigSync.load(this.plugin, tagsFileLive, "modules/tags/config.yml");
      this.catalog.importTagsFrom(liveTags);
   }

   private void loadYamls() {
      File folder = this.moduleFolder();
      this.shop = ConfigSync.load(this.plugin, new File(folder, "itemshop.yml"), this.jarResourcePath("itemshop.yml"));
      this.tokens = ConfigSync.load(this.plugin, new File(folder, "tokens.yml"), this.jarResourcePath("tokens.yml"));
      this.raritiesYaml = ConfigSync.load(this.plugin, new File(folder, "rarities.yml"), this.jarResourcePath("rarities.yml"));
      this.hatsFile = new File(folder, "hats.yml");
      this.tagsFile = new File(folder, "tags.yml");
      this.hatsYaml = ConfigSync.load(this.plugin, this.hatsFile, this.jarResourcePath("hats.yml"));
      this.tagsYaml = ConfigSync.load(this.plugin, this.tagsFile, this.jarResourcePath("tags.yml"));
      boolean stale = this.shop.getInt("config-version", 0) < 7
         || this.hatsYaml.contains("hats.crystal_crown")
         || this.hatsYaml.getInt("config-version", 0) < 6;
      this.replaceIfOld("itemshop.yml", stale || this.shop.getInt("config-version", 0) < 10);
      this.replaceIfOld("messages.yml", this.messages.getInt("config-version", 0) < 4);
      this.replaceIfOld("rarities.yml", this.raritiesYaml.getInt("config-version", 0) < 2);
      this.replaceIfOld("hats.yml", stale || this.hatsYaml.getInt("config-version", 0) < 6);
      this.replaceIfOld("tags.yml", stale || this.tagsYaml.getInt("config-version", 0) < 4);
      this.shop = YamlConfiguration.loadConfiguration(new File(folder, "itemshop.yml"));
      this.migrateShopLayout();
      this.raritiesYaml = YamlConfiguration.loadConfiguration(new File(folder, "rarities.yml"));
      this.messages = YamlConfiguration.loadConfiguration(new File(folder, "messages.yml"));
      this.hatsYaml = YamlConfiguration.loadConfiguration(this.hatsFile);
      this.tagsYaml = YamlConfiguration.loadConfiguration(this.tagsFile);
   }

   private void migrateShopLayout() {
      if (this.shop.getInt("shop-layout", 0) >= 2) {
         return;
      }
      this.shop.set("shop-item.lore", List.of(
         "&7[ɪᴛᴇᴍsʜᴏᴘ]",
         "",
         "&x&5&C&9&4&F&CDescription:",
         "&x&5&C&9&4&F&C| &x&5&C&9&4&F&CClick Here,&f To",
         "&x&5&C&9&4&F&C| &fPurchase This &x&5&C&9&4&F&CCosmetic",
         "",
         "&x&5&C&9&4&F&C🌊 &fPrice: &x&5&C&9&4&F&C%price%",
         "",
         "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Purchase"
      ));
      this.shop.set("gui.shop.items.previous.slot", 48);
      this.shop.set("gui.shop.items.countdown.slot", 49);
      this.shop.set("gui.shop.items.next.slot", 50);
      this.shop.set("gui.shop.items.my-cosmetics.slot", 51);
      this.shop.set("gui.shop.items.my-tags.slot", 53);
      this.shop.set("gui.shop.items.limited.slot", 46);
      this.shop.set("gui.shop.items.back.slot", 46);
      this.shop.set("limited.daily-slots", this.shop.getInt("limited.daily-slots", 4));
      this.shop.set("limited.shulkers.halloween", "ORANGE_SHULKER_BOX");
      this.shop.set("limited.shulkers.christmas", "GREEN_SHULKER_BOX");
      this.shop.set("limited.shulkers.easter", "PINK_SHULKER_BOX");
      this.shop.set("shop-layout", 2);
      this.saveShop();
   }

   private void replaceIfOld(String file, boolean old) {
      if (old) {
         this.plugin.saveResource(this.jarResourcePath(file), true);
      }
   }

   void startTasks() {
      if (this.ticker != null) {
         this.ticker.cancel();
      }
      if (this.expiry != null) {
         this.expiry.cancel();
      }
      long ticks = Math.max(1L, this.shop.getLong("countdown-update-ticks", 20L));
      this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> this.guis.tickOpenShops(), ticks, ticks);
      this.expiry = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
         if (System.currentTimeMillis() >= this.rotation.expiresAt()) {
            this.refreshRotation(true);
         }
      }, 100L, 100L);
   }

   void refreshRotation(boolean announce) {
      this.async(() -> {
         this.rotation.roll(this.shop);
         this.sync(() -> {
            this.rollLimited();
            this.guis.refreshOpenShops();
               if (announce && this.shop.getBoolean("rotation.broadcast.enabled", true)) {
               String message = this.shop.getString("rotation.broadcast.message", "");
               if (!message.isBlank()) {
                  Bukkit.getServer().sendMessage(Text.c(message));
               }
               var sound = this.shop.getConfigurationSection("rotation.broadcast.sound");
               for (Player player : Bukkit.getOnlinePlayers()) {
                  ItemShopItems.play(player, sound);
               }
            }
         });
      });
   }

   private boolean canUseShop(Player player) {
      return player.hasPermission("shardedcore.itemshop")
         || player.hasPermission("sharded.itemshop")
         || player.hasPermission("sharded.itemshop.use")
         || player.isOp();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         if (sender instanceof Player player) {
            if (!this.canUseShop(player)) {
               this.msg(sender, "no-permission");
               return true;
            }
            this.guis.openShop(player);
            return true;
         }
         this.msg(sender, "players-only");
         return true;
      }
      String sub = args[0].toLowerCase(Locale.ROOT);
      if (sub.equals("cosmetics")) {
         if (sender instanceof Player player) {
            if (!this.canUseShop(player)) {
               this.msg(sender, "no-permission");
               return true;
            }
            player.closeInventory();
            player.performCommand("wardrobe");
            return true;
         }
         this.msg(sender, "players-only");
         return true;
      }
      if (!sender.hasPermission("shardedcore.itemshop.admin")) {
         this.msg(sender, "no-permission");
         return true;
      }
      if (sub.equals("reload")) {
         this.reload();
         this.msg(sender, "reloaded");
         return true;
      }
      if (sub.equals("refresh")) {
         this.refreshRotation(true);
         this.msg(sender, "refreshed");
         return true;
      }
      if ((sub.equals("give") || sub.equals("take")) && args.length >= 4) {
         OfflinePlayer target = OfflinePlayers.resolve(args[1]);
         if (target == null || target.getUniqueId() == null) {
            this.msg(sender, "unknown-player", "%player%", args[1]);
            return true;
         }
         String type = this.normalizeType(args[2]);
         if (type == null) {
            this.msg(sender, "unknown-type");
            return true;
         }
         ItemShopTypes.Cosmetic cosmetic = this.catalog.cosmetic(type, args[3]);
         if (cosmetic == null) {
            this.msg(sender, "unknown-item", "%type%", type, "%id%", args[3]);
            return true;
         }
         if (sub.equals("give")) {
            this.grant(target.getUniqueId(), type, cosmetic.id(), false);
            this.unlockExternal(target.getUniqueId(), type, cosmetic.id(), Bukkit.getPlayer(target.getUniqueId()));
            this.msg(sender, "given", "%type%", type, "%id%", cosmetic.id(), "%player%", target.getName() == null ? args[1] : target.getName());
         } else {
            this.revoke(target.getUniqueId(), type, cosmetic.id());
            this.msg(sender, "taken", "%type%", type, "%id%", cosmetic.id(), "%player%", target.getName() == null ? args[1] : target.getName());
         }
         return true;
      }
      if (sub.equals("season") && args.length >= 2) {
         String season = this.normalizeSeason(args[1]);
         if (season == null) {
            this.msg(sender, "unknown-season");
            return true;
         }
         this.shop.set("limited.active-season", season);
         this.rollLimited();
         this.msg(sender, "season-set", "%season%", season);
         this.guis.refreshOpenShops();
         return true;
      }
      if (sub.equals("limited") && args.length >= 2 && args[1].equalsIgnoreCase("add")) {
         if (args.length < 4) {
            this.msg(sender, "usage");
            return true;
         }
         String season = this.normalizeSeason(args[3]);
         if (season == null) {
            this.msg(sender, "unknown-season");
            return true;
         }
         ItemShopTypes.Cosmetic hat = this.catalog.cosmetic("hat", args[2]);
         if (hat == null) {
            this.msg(sender, "unknown-item", "%type%", "hat", "%id%", args[2]);
            return true;
         }
         String path = "limited.seasons." + season;
         List<String> listed = new ArrayList<>(this.shop.getStringList(path));
         if (listed.stream().noneMatch(id -> id.equalsIgnoreCase(hat.id()))) {
            listed.add(hat.id());
            this.shop.set(path, listed);
            if (season.equals(this.activeSeason())) {
               List<String> shown = new ArrayList<>(this.shop.getStringList("limited.shown"));
               int slots = Math.max(1, this.shop.getInt("limited.daily-slots", 4));
               if (shown.size() < slots && shown.stream().noneMatch(id -> id.equalsIgnoreCase(hat.id()))) {
                  shown.add(hat.id());
                  this.shop.set("limited.shown", shown);
                  this.shop.set("limited.shown-season", season);
               }
            }
            this.saveShop();
         }
         this.guis.refreshOpenShops();
         this.msg(sender, "limited-added", "%id%", hat.id(), "%season%", season);
         return true;
      }
      if (sub.equals("setprice") && args.length >= 4) {
         String type = this.normalizeType(args[1]);
         if (type == null) {
            this.msg(sender, "unknown-type");
            return true;
         }
         ItemShopTypes.Cosmetic cosmetic = this.catalog.cosmetic(type, args[2]);
         if (cosmetic == null) {
            this.msg(sender, "unknown-item", "%type%", type, "%id%", args[2]);
            return true;
         }
         long price;
         try {
            price = Long.parseLong(args[3]);
         } catch (NumberFormatException ex) {
            this.msg(sender, "invalid-price");
            return true;
         }
         if (price < 0L) {
            this.msg(sender, "invalid-price");
            return true;
         }
         this.catalog.setPrice(type, cosmetic.id(), price);
         YamlConfiguration file = "tag".equals(type) ? this.tagsYaml : this.hatsYaml;
         File disk = "tag".equals(type) ? this.tagsFile : this.hatsFile;
         file.set(type.equals("tag") ? "tags." + cosmetic.id() + ".price" : "hats." + cosmetic.id() + ".price", price);
         try {
            file.save(disk);
         } catch (Exception ignored) {
         }
         this.msg(sender, "price-set", "%type%", type, "%id%", cosmetic.id(), "%price%", String.valueOf(price), "%currency%", this.currencyName());
         return true;
      }
      this.msg(sender, "usage");
      return true;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1) {
         List<String> opts = new ArrayList<>(List.of("cosmetics"));
         if (sender.hasPermission("shardedcore.itemshop.admin")) {
            opts.addAll(List.of("refresh", "reload", "give", "take", "setprice", "season", "limited"));
         }
         return TabCompleteHelper.filter(args[0], opts);
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("season")) {
         return TabCompleteHelper.filter(args[1], "halloween", "christmas", "easter");
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("limited")) {
         return TabCompleteHelper.filter(args[1], "add");
      }
      if (args.length == 3 && args[0].equalsIgnoreCase("limited") && args[1].equalsIgnoreCase("add")) {
         return TabCompleteHelper.filter(args[2], this.catalog.hatIds());
      }
      if (args.length == 4 && args[0].equalsIgnoreCase("limited") && args[1].equalsIgnoreCase("add")) {
         return TabCompleteHelper.filter(args[3], "halloween", "christmas", "easter");
      }
      if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
         return TabCompleteHelper.knownPlayers(args[1]);
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("setprice")) {
         return TabCompleteHelper.filter(args[1], "hat", "tag");
      }
      if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
         return TabCompleteHelper.filter(args[2], "hat", "tag");
      }
      if (args.length == 4 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))
         || args.length == 3 && args[0].equalsIgnoreCase("setprice")) {
         String type = this.normalizeType(args[0].equalsIgnoreCase("setprice") ? args[1] : args[2]);
         return TabCompleteHelper.filter(
            args[args.length - 1],
            "tag".equals(type) ? this.catalog.tagIds() : this.catalog.hatIds()
         );
      }
      return List.of();
   }

   private String normalizeType(String raw) {
      if (raw == null) {
         return null;
      }
      String s = raw.toLowerCase(Locale.ROOT);
      if (s.equals("hat") || s.equals("hats")) {
         return "hat";
      }
      return s.equals("tag") || s.equals("tags") ? "tag" : null;
   }

   private String normalizeSeason(String raw) {
      if (raw == null) {
         return null;
      }
      return switch (raw.toLowerCase(Locale.ROOT)) {
         case "halloween", "christmas", "easter" -> raw.toLowerCase(Locale.ROOT);
         default -> null;
      };
   }

   String activeSeason() {
      String season = this.normalizeSeason(this.shop.getString("limited.active-season", "halloween"));
      return season == null ? "halloween" : season;
   }

   List<String> limitedHats() {
      return List.copyOf(this.shop.getStringList("limited.seasons." + this.activeSeason()));
   }

   Set<String> limitedIdSet() {
      Set<String> ids = new java.util.LinkedHashSet<>();
      ConfigurationSection seasons = this.shop.getConfigurationSection("limited.seasons");
      if (seasons != null) {
         for (String season : seasons.getKeys(false)) {
            for (String id : this.shop.getStringList("limited.seasons." + season)) {
               if (id != null && !id.isBlank()) {
                  ids.add(id.toLowerCase(Locale.ROOT));
               }
            }
         }
      }
      WardrobeModule wardrobe = this.plugin.modules().get(WardrobeModule.class);
      if (wardrobe != null) {
         for (String id : wardrobe.limitedHatIds()) {
            if (id != null && !id.isBlank()) {
               ids.add(id.toLowerCase(Locale.ROOT));
            }
         }
      }
      return ids;
   }

   List<ItemShopTypes.Cosmetic> shopHats(boolean limitedTab) {
      return this.browserHats(limitedTab);
   }

   List<ItemShopTypes.Cosmetic> shopTags(boolean limitedTab) {
      com.sharded.core.modules.tags.TagsModule tags = this.plugin.modules().get(com.sharded.core.modules.tags.TagsModule.class);
      java.util.Set<String> seasonal = tags == null ? java.util.Set.of() : tags.seasonLimitedIds();
      java.util.Set<String> active = tags == null ? java.util.Set.of() : tags.activeSeasonTagIds();
      List<ItemShopTypes.Cosmetic> list = new ArrayList<>();
      java.util.Set<String> seen = new java.util.HashSet<>();
      for (ItemShopTypes.Cosmetic tag : this.catalog.tags()) {
         if (!tag.enabled()) {
            continue;
         }
         String id = tag.id().toLowerCase(Locale.ROOT);
         boolean seasonalTag = seasonal.contains(id);
         if (limitedTab) {
            if (!active.contains(id)) {
               continue;
            }
         } else if (seasonalTag) {
            continue;
         }
         list.add(tag);
         seen.add(id);
      }
      if (tags != null) {
         for (String id : (limitedTab ? active : tags.allTagIds())) {
            String key = id.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
               continue;
            }
            if (!limitedTab && seasonal.contains(key)) {
               continue;
            }
            if (limitedTab && !active.contains(key)) {
               continue;
            }
            ItemShopTypes.Cosmetic cosmetic = this.catalog.tag(key);
            if (cosmetic != null) {
               if (cosmetic.enabled()) {
                  list.add(cosmetic);
               }
               continue;
            }
            list.add(this.tagCosmetic(key, tags.tagLabel(key)));
         }
      }
      return list;
   }

   private ItemShopTypes.Cosmetic tagCosmetic(String id, String label) {
      return new ItemShopTypes.Cosmetic(
         "tag",
         id,
         label == null || label.isBlank() ? id : label,
         List.of("Equip this tag in chat."),
         "common",
         null,
         true,
         true,
         false,
         "sharded.tag." + id,
         List.of(),
         "NAME_TAG",
         "",
         "",
         0.0,
         0.55,
         0.0,
         0.4F,
         0F,
         0F,
         0F,
         "",
         "before"
      );
   }

   List<ItemShopTypes.Cosmetic> browserHats(boolean limitedTab) {
      Set<String> limited = this.limitedIdSet();
      List<ItemShopTypes.Cosmetic> list = new ArrayList<>();
      if (limitedTab) {
         for (String id : limited) {
            ItemShopTypes.Cosmetic hat = this.catalog.hat(id);
            if (hat != null && hat.enabled() && !HatCatalog.isRemoved(hat.id(), hat.material())) {
               list.add(hat);
            }
         }
         return list;
      }
      for (ItemShopTypes.Cosmetic hat : this.catalog.hats()) {
         if (!hat.enabled() || HatCatalog.isRemoved(hat.id(), hat.material())) {
            continue;
         }
         if (limited.contains(hat.id().toLowerCase(Locale.ROOT))) {
            continue;
         }
         list.add(hat);
      }
      return list;
   }

   List<String> limitedShown() {
      if (!this.activeSeason().equalsIgnoreCase(this.shop.getString("limited.shown-season", ""))) {
         return List.of();
      }
      return List.copyOf(this.shop.getStringList("limited.shown"));
   }

   Material seasonShulker() {
      String raw = this.shop.getString("limited.shulkers." + this.activeSeason(), "");
      if (raw == null || raw.isBlank()) {
         raw = switch (this.activeSeason()) {
            case "christmas" -> "GREEN_SHULKER_BOX";
            case "easter" -> "PINK_SHULKER_BOX";
            default -> "ORANGE_SHULKER_BOX";
         };
      }
      Material material = Material.matchMaterial(raw);
      return material == null ? Material.ORANGE_SHULKER_BOX : material;
   }

   void ensureLimitedCurrent() {
      String season = this.activeSeason();
      boolean seasonChanged = !season.equalsIgnoreCase(this.shop.getString("limited.shown-season", ""));
      boolean expired = this.shop.getLong("limited.shown-expires", 0L) != this.rotation.expiresAt();
      if (seasonChanged || expired || (this.limitedShown().isEmpty() && !this.limitedHats().isEmpty())) {
         this.rollLimited();
      }
   }

   void rollLimited() {
      String season = this.activeSeason();
      List<String> pool = new ArrayList<>();
      for (String id : this.shop.getStringList("limited.seasons." + season)) {
         if (this.catalog.cosmetic("hat", id) != null && pool.stream().noneMatch(id::equalsIgnoreCase)) {
            pool.add(id);
         }
      }
      List<String> previous = new ArrayList<>(this.shop.getStringList("limited.shown"));
      List<String> fresh = new ArrayList<>();
      List<String> repeats = new ArrayList<>();
      for (String id : pool) {
         if (previous.stream().anyMatch(id::equalsIgnoreCase)) {
            repeats.add(id);
         } else {
            fresh.add(id);
         }
      }
      java.util.Collections.shuffle(fresh);
      java.util.Collections.shuffle(repeats);
      int count = Math.max(1, this.shop.getInt("limited.daily-slots", 4));
      List<String> shown = new ArrayList<>();
      for (String id : fresh) {
         if (shown.size() >= count) {
            break;
         }
         shown.add(id);
      }
      for (String id : repeats) {
         if (shown.size() >= count) {
            break;
         }
         shown.add(id);
      }
      this.shop.set("limited.shown", shown);
      this.shop.set("limited.shown-season", season);
      this.shop.set("limited.shown-expires", this.rotation.expiresAt());
      this.saveShop();
   }

   boolean isLimitedListing(String type, String id) {
      if (!"hat".equals(type) || id == null) {
         return false;
      }
      for (String hat : this.limitedShown()) {
         if (hat.equalsIgnoreCase(id)) {
            return true;
         }
      }
      return false;
   }

   private void saveShop() {
      try {
         this.shop.save(new File(this.moduleFolder(), "itemshop.yml"));
      } catch (Exception ignored) {
      }
   }

   boolean tryPurchase(Player player, String type, String id) {
      ItemShopTypes.Cosmetic cosmetic = this.catalog.cosmetic(type, id);
      if (cosmetic == null || !cosmetic.enabled()) {
         this.msg(player, "unknown-item", "%type%", type, "%id%", id);
         return false;
      }
      if (!cosmetic.allowedOn(this.serverId())) {
         this.msg(player, "wrong-server");
         return false;
      }
      if ("hat".equals(cosmetic.type())) {
         if (HatCatalog.isRemoved(cosmetic.id(), cosmetic.material())) {
            this.msg(player, "not-in-shop");
            return false;
         }
      } else if (!this.rotation.contains(type, id) && !this.isLimitedListing(type, id)) {
         this.msg(player, "not-in-shop");
         return false;
      }
      if (this.owns(player.getUniqueId(), type, id)) {
         this.msg(player, "already-owned");
         return false;
      }
      if (this.rankLocked(player, cosmetic)) {
         this.msg(player, "locked");
         return false;
      }
      long price = this.catalog.price(cosmetic);
      TokenService tokens = this.plugin.modules().tokens();
      if (tokens == null || tokens.getBalance(player.getUniqueId()) < price || !tokens.take(player.getUniqueId(), price)) {
         this.msg(player, "not-enough", "%price%", String.valueOf(price), "%currency%", this.currencyName());
         ItemShopItems.play(player, this.shop.getConfigurationSection("sounds.purchase-fail"));
         return false;
      }
      this.grant(player.getUniqueId(), type, id, true);
      this.unlockExternal(player.getUniqueId(), type, id, player);
      long balance = tokens.getBalance(player.getUniqueId());
      this.async(() -> {
         this.database.logTransaction(player.getUniqueId(), -price, "itemshop_purchase", type + ":" + id);
         this.database.logPurchase(player.getUniqueId(), type, id, cosmetic.rarityId(), price);
         this.database.upsertPlayerTokens(player.getUniqueId(), balance);
      });
      this.msg(player, "purchased", "%item%", cosmetic.displayName(), "%price%", String.valueOf(price), "%currency%", this.currencyName());
      ItemShopTypes.Rarity rarity = this.catalog.rarity(cosmetic.rarityId());
      if (rarity != null) {
         player.playSound(player.getLocation(), rarity.purchaseSound(), rarity.purchaseVolume(), rarity.purchasePitch());
         if (rarity.broadcastOnPurchase() && rarity.broadcastMessage() != null && !rarity.broadcastMessage().isBlank()) {
            Bukkit.getServer().sendMessage(Text.c(ItemShopItems.apply(
               rarity.broadcastMessage(),
               "%player%", player.getName(),
               "%item%", cosmetic.displayName()
            )));
         }
      } else {
         ItemShopItems.play(player, this.shop.getConfigurationSection("sounds.purchase-success"));
      }
      if (this.shop.getBoolean("auto-equip-on-purchase", true)) {
         this.equip(player, type, id);
      }
      return true;
   }

   void grant(UUID uuid, String type, String id, boolean fromShop) {
      this.owned(type).computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet()).add(id.toLowerCase(Locale.ROOT));
      this.async(() -> this.database.grant(uuid, type, id.toLowerCase(Locale.ROOT)));
   }

   public void refreshShops() {
      if (this.guis != null) {
         this.guis.refreshOpenShops();
      }
   }

   public void setHatListed(String hatId, boolean listed) {
      if (hatId == null || hatId.isBlank() || this.hatsYaml == null) {
         return;
      }
      String key = hatId.toLowerCase(Locale.ROOT);
      if (key.contains(":")) {
         key = key.substring(key.lastIndexOf(':') + 1);
      }
      if (!this.hatsYaml.contains("hats." + key) && this.catalog.hat(hatId) == null && this.catalog.hat(key) == null) {
         return;
      }
      String path = this.hatsYaml.contains("hats." + key) ? "hats." + key : "hats." + hatId.toLowerCase(Locale.ROOT);
      this.hatsYaml.set(path + ".enabled", listed);
      this.hatsYaml.set(path + ".in-rotation", listed);
      try {
         this.hatsYaml.save(this.hatsFile);
      } catch (Exception ignored) {
      }
      ItemShopTypes.Cosmetic cosmetic = this.catalog.hat(key);
      if (cosmetic == null) {
         cosmetic = this.catalog.hat(hatId);
      }
      if (cosmetic != null) {
         this.catalog.setListed(cosmetic.type(), cosmetic.id(), listed);
      }
   }

   void revoke(UUID uuid, String type, String id) {
      Set<String> set = this.owned(type).get(uuid);
      if (set != null) {
         set.remove(id.toLowerCase(Locale.ROOT));
      }
      if (type.equals("hat") && id.equalsIgnoreCase(this.equippedHat.getOrDefault(uuid, ""))) {
         this.equippedHat.remove(uuid);
         Player player = Bukkit.getPlayer(uuid);
         if (player != null) {
            this.hats.remove(player);
         }
      }
      if (type.equals("tag") && id.equalsIgnoreCase(this.equippedTag.getOrDefault(uuid, ""))) {
         this.equippedTag.remove(uuid);
         Player player = Bukkit.getPlayer(uuid);
         if (player != null && this.plugin.cosmetics() != null) {
            this.plugin.cosmetics().clearTag(player);
         }
      }
      this.async(() -> {
         this.database.revoke(uuid, type, id.toLowerCase(Locale.ROOT));
         this.database.saveEquipped(uuid, this.equippedHat.get(uuid), this.equippedTag.get(uuid));
      });
   }

   void equip(Player player, String type, String id) {
      if (!this.owns(player.getUniqueId(), type, id)) {
         this.msg(player, "not-owned");
         return;
      }
      ItemShopTypes.Cosmetic cosmetic = this.catalog.cosmetic(type, id);
      if (cosmetic == null) {
         return;
      }
      if ("hat".equals(type)) {
         this.equippedHat.put(player.getUniqueId(), id.toLowerCase(Locale.ROOT));
         this.applyHat(player);
         this.msg(player, "equipped-hat", "%item%", cosmetic.displayName());
      } else {
         this.equippedTag.put(player.getUniqueId(), id.toLowerCase(Locale.ROOT));
         this.applyTag(player);
         this.msg(player, "equipped-tag", "%item%", cosmetic.displayName());
      }
      this.async(() -> this.database.saveEquipped(player.getUniqueId(), this.equippedHat.get(player.getUniqueId()), this.equippedTag.get(player.getUniqueId())));
   }

   void unequip(Player player, String type) {
      if ("hat".equals(type)) {
         this.equippedHat.remove(player.getUniqueId());
         this.hats.remove(player);
         this.msg(player, "unequipped-hat");
      } else {
         this.equippedTag.remove(player.getUniqueId());
         if (this.plugin.cosmetics() != null) {
            this.plugin.cosmetics().clearTag(player);
         }
         this.msg(player, "unequipped-tag");
      }
      this.async(() -> this.database.saveEquipped(player.getUniqueId(), this.equippedHat.get(player.getUniqueId()), this.equippedTag.get(player.getUniqueId())));
   }

   void unequipAll(Player player) {
      this.equippedHat.remove(player.getUniqueId());
      this.equippedTag.remove(player.getUniqueId());
      this.hats.remove(player);
      if (this.plugin.cosmetics() != null) {
         this.plugin.cosmetics().clearTag(player);
      }
      this.async(() -> this.database.saveEquipped(player.getUniqueId(), null, null));
      this.msg(player, "unequipped-all");
   }

   void applyVisuals(Player player) {
      this.applyHat(player);
      this.applyTag(player);
   }

   void applyHat(Player player) {
      String id = this.equippedHat.get(player.getUniqueId());
      ItemShopTypes.Cosmetic hat = id == null ? null : this.catalog.hat(id);
      this.hats.apply(player, hat, this.shop.getStringList("hidden-worlds"), this.shop.getBoolean("hats.hidden-in-spectator", true));
   }

   void applyTag(Player player) {
      String id = this.equippedTag.get(player.getUniqueId());
      ItemShopTypes.Cosmetic tag = id == null ? null : this.catalog.tag(id);
      if (tag == null || this.plugin.cosmetics() == null) {
         return;
      }
      this.plugin.cosmetics().setTag(player, "itemshop:" + tag.id(), tag.tagText());
   }

   void unlockExternal(UUID uuid, String type, String id, Player player) {
      if ("hat".equals(type)) {
         var wardrobe = this.plugin.modules().get(com.sharded.core.modules.wardrobe.WardrobeModule.class);
         if (wardrobe != null) {
            wardrobe.unlock(uuid, id, null);
         }
      } else if ("tag".equals(type)) {
         var tags = this.plugin.modules().get(com.sharded.core.modules.tags.TagsModule.class);
         if (tags != null) {
            tags.unlock(uuid, id, null);
         } else if (player != null && this.plugin.luckPerms() != null) {
            this.plugin.luckPerms().runConsole("lp user " + player.getName() + " permission set sharded.tag." + id + " true");
         }
      }
   }

   boolean rankLocked(Player player, ItemShopTypes.Cosmetic cosmetic) {
      String perm = cosmetic.permission();
      if (perm == null || perm.isBlank()) {
         return false;
      }
      if (perm.startsWith("sharded.wardrobe.") || perm.equals("sharded.tag." + cosmetic.id())) {
         return false;
      }
      return !player.hasPermission(perm);
   }

   boolean owns(UUID uuid, String type, String id) {
      if (id == null) {
         return false;
      }
      String key = id.toLowerCase(Locale.ROOT);
      if (this.owned(type).getOrDefault(uuid, Set.of()).contains(key)) {
         return true;
      }
      ItemShopTypes.Cosmetic cosmetic = this.catalog.cosmetic(type, key);
      if ("hat".equals(type)) {
         var wardrobe = this.plugin.modules().get(com.sharded.core.modules.wardrobe.WardrobeModule.class);
         if (wardrobe != null) {
            if (wardrobe.ownsHat(uuid, key)) {
               return true;
            }
            if (cosmetic != null && cosmetic.material() != null && !cosmetic.material().isBlank() && wardrobe.ownsHat(uuid, cosmetic.material())) {
               return true;
            }
         }
         return false;
      }
      var tags = this.plugin.modules().get(com.sharded.core.modules.tags.TagsModule.class);
      if (tags != null && tags.isUnlocked(uuid, key)) {
         return true;
      }
      Player player = Bukkit.getPlayer(uuid);
      return player != null && tags != null && tags.owns(player, key);
   }

   private Map<UUID, Set<String>> owned(String type) {
      return "tag".equals(type) ? this.ownedTags : this.ownedHats;
   }

   Set<String> ownedIds(UUID uuid, String type) {
      return new HashSet<>(this.owned(type).getOrDefault(uuid, Set.of()));
   }

   String equipped(UUID uuid, String type) {
      return "tag".equals(type) ? this.equippedTag.get(uuid) : this.equippedHat.get(uuid);
   }

   public String placeholderHat(UUID uuid) {
      String id = this.equippedHat.get(uuid);
      ItemShopTypes.Cosmetic hat = id == null ? null : this.catalog.hat(id);
      return hat == null ? "" : hat.displayName();
   }

   public String placeholderTag(UUID uuid) {
      String id = this.equippedTag.get(uuid);
      ItemShopTypes.Cosmetic tag = id == null ? null : this.catalog.tag(id);
      return tag == null || tag.tagText() == null ? "" : tag.tagText();
   }

   public String tagBefore(Player player) {
      ItemShopTypes.Cosmetic tag = this.equippedTagCosmetic(player.getUniqueId());
      if (tag == null || !"before".equalsIgnoreCase(tag.position())) {
         return "";
      }
      return tag.tagText();
   }

   public String tagAfter(Player player) {
      ItemShopTypes.Cosmetic tag = this.equippedTagCosmetic(player.getUniqueId());
      if (tag == null || !"after".equalsIgnoreCase(tag.position())) {
         return "";
      }
      return tag.tagText();
   }

   private ItemShopTypes.Cosmetic equippedTagCosmetic(UUID uuid) {
      String id = this.equippedTag.get(uuid);
      return id == null ? null : this.catalog.tag(id);
   }

   String currencyName() {
      return this.tokens.getString("currency.name", "Tokens");
   }

   String currencySymbol() {
      return this.tokens.getString("currency.symbol", "⛃");
   }

   String serverId() {
      return this.shop.getString("server-id", "CrystalPVP");
   }

   YamlConfiguration shop() {
      return this.shop;
   }

   YamlConfiguration tokensYaml() {
      return this.tokens;
   }

   ItemShopCatalog catalog() {
      return this.catalog;
   }

   ItemShopRotation rotation() {
      return this.rotation;
   }

   HatDisplayManager hatDisplays() {
      return this.hats;
   }

   ShardedCore plugin() {
      return this.plugin;
   }

   String formatCountdown() {
      long remaining = Math.max(0L, (this.rotation.expiresAt() - System.currentTimeMillis()) / 1000L);
      long hours = remaining / 3600L;
      long minutes = remaining % 3600L / 60L;
      long seconds = remaining % 60L;
      return ItemShopItems.apply(
         this.shop.getString("countdown-format", "{hours}h {minutes}m {seconds}s"),
         "{hours}", String.valueOf(hours),
         "{minutes}", String.valueOf(minutes),
         "{seconds}", String.valueOf(seconds)
      );
   }

   String status(Player player, ItemShopTypes.Cosmetic cosmetic) {
      if (this.owns(player.getUniqueId(), cosmetic.type(), cosmetic.id())) {
         return this.shop.getString("shop-item.status.owned", "&#94FF00&lYOU ALREADY OWN THIS");
      }
      if (this.rankLocked(player, cosmetic)) {
         return this.shop.getString("shop-item.status.locked", "LOCKED");
      }
      TokenService tokens = this.plugin.modules().tokens();
      long price = this.catalog.price(cosmetic);
      if (tokens == null || tokens.getBalance(player.getUniqueId()) < price) {
         return this.shop.getString("shop-item.status.not-enough", "NOT ENOUGH TOKENS");
      }
      return this.shop.getString("shop-item.status.click-to-buy", "CLICK TO BUY");
   }

   void msg(CommandSender sender, String key, String... replacements) {
      sender.sendMessage(Text.c(this.raw(key, replacements)));
   }

   void async(Runnable runnable) {
      Bukkit.getScheduler().runTaskAsynchronously(this.plugin, runnable);
   }

   void sync(Runnable runnable) {
      Bukkit.getScheduler().runTask(this.plugin, runnable);
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      this.sync(() -> this.applyVisuals(event.getPlayer()));
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.hats.remove(event.getPlayer());
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      if (this.shop.getBoolean("hats.hidden-on-death", true)) {
         this.hats.remove(event.getEntity());
      }
   }

   @EventHandler
   public void onRespawn(PlayerRespawnEvent event) {
      this.sync(() -> this.applyHat(event.getPlayer()));
   }

   @EventHandler
   public void onWorld(PlayerChangedWorldEvent event) {
      this.applyHat(event.getPlayer());
   }

   @EventHandler
   public void onGameMode(PlayerGameModeChangeEvent event) {
      this.sync(() -> {
         if (event.getNewGameMode() == GameMode.SPECTATOR) {
            this.hats.remove(event.getPlayer());
         } else {
            this.applyHat(event.getPlayer());
         }
      });
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHideCommand(PlayerCommandPreprocessEvent event) {
      String raw = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
      if (raw.contains(":")) {
         raw = raw.substring(raw.indexOf(':') + 1);
      }
      if (this.shop.getStringList("hide-commands").stream().anyMatch(raw::equalsIgnoreCase)) {
         this.sync(() -> this.hats.remove(event.getPlayer()));
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onClick(InventoryClickEvent event) {
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }
      Object holder = TrackedInventories.lookup(event.getView().getTopInventory());
      if (!(holder instanceof ShopHolder) && !(holder instanceof ConfirmHolder) && !(holder instanceof CosmeticsHolder) && !(holder instanceof LimitedHolder)
         && !(event.getView().getTopInventory().getHolder() instanceof ShopHolder)
         && !(event.getView().getTopInventory().getHolder() instanceof ConfirmHolder)
         && !(event.getView().getTopInventory().getHolder() instanceof CosmeticsHolder)
         && !(event.getView().getTopInventory().getHolder() instanceof LimitedHolder)) {
         return;
      }
      event.setCancelled(true);
      event.setResult(org.bukkit.event.Event.Result.DENY);
      if (event.getClick() == ClickType.NUMBER_KEY || event.getClick() == ClickType.SWAP_OFFHAND || event.getClick() == ClickType.DOUBLE_CLICK) {
         return;
      }
      if (event.getClickedInventory() != event.getView().getTopInventory()) {
         return;
      }
      this.guis.handleClick(player, event);
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onDrag(InventoryDragEvent event) {
      Object holder = TrackedInventories.lookup(event.getView().getTopInventory());
      if (holder instanceof ShopHolder || holder instanceof ConfirmHolder || holder instanceof CosmeticsHolder || holder instanceof LimitedHolder
         || event.getView().getTopInventory().getHolder() instanceof ShopHolder
         || event.getView().getTopInventory().getHolder() instanceof ConfirmHolder
         || event.getView().getTopInventory().getHolder() instanceof CosmeticsHolder
         || event.getView().getTopInventory().getHolder() instanceof LimitedHolder) {
         event.setCancelled(true);
         event.setResult(org.bukkit.event.Event.Result.DENY);
      }
   }

   static final class ShopHolder implements InventoryHolder {
      org.bukkit.inventory.Inventory inventory;
      int page;

      public org.bukkit.inventory.Inventory getInventory() {
         return this.inventory;
      }
   }

   static final class LimitedHolder implements InventoryHolder {
      org.bukkit.inventory.Inventory inventory;
      int page;

      public org.bukkit.inventory.Inventory getInventory() {
         return this.inventory;
      }
   }

   static final class ConfirmHolder implements InventoryHolder {
      org.bukkit.inventory.Inventory inventory;
      String type;
      String id;
      String back = "shop";

      public org.bukkit.inventory.Inventory getInventory() {
         return this.inventory;
      }
   }

   static final class CosmeticsHolder implements InventoryHolder {
      org.bukkit.inventory.Inventory inventory;
      String filter = "all";
      int page;

      public org.bukkit.inventory.Inventory getInventory() {
         return this.inventory;
      }
   }
}

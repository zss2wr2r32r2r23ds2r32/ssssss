package com.sharded.core.modules.itemshop;

import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class ItemShopGuis {
   private final ItemShopModule module;
   private final NamespacedKey clickKey;

   ItemShopGuis(ItemShopModule module) {
      this.module = module;
      this.clickKey = new NamespacedKey(module.plugin(), "itemshop-click");
   }

   void openShop(Player player) {
      this.openShop(player, 0);
   }

   void openShop(Player player, int page) {
      YamlConfiguration shop = this.module.shop();
      ConfigurationSection gui = shop.getConfigurationSection("gui.shop");
      int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));
      int pages = this.pageCount(gui, false);
      ItemShopModule.ShopHolder holder = new ItemShopModule.ShopHolder();
      holder.page = Math.max(0, Math.min(page, pages - 1));
      Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.c(gui.getString("title", "&8Item Shop")));
      holder.inventory = inventory;
      TrackedInventories.track(inventory, holder);
      this.paintShop(player, inventory, gui, holder.page, false);
      player.openInventory(inventory);
      ItemShopItems.play(player, shop.getConfigurationSection("sounds.click"));
   }

   void paintShop(Player player, Inventory inventory, ConfigurationSection gui, int page) {
      this.paintShop(player, inventory, gui, page, false);
   }

   void paintShop(Player player, Inventory inventory, ConfigurationSection gui, int page, boolean limitedTab) {
      Material fillerMat = ItemShopTypes.material(gui, "filler-material", Material.BLACK_STAINED_GLASS_PANE);
      ItemStack filler = ItemShopItems.fromSection(gui, fillerMat, gui.getString("filler-name", " "), List.of());
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, filler.clone());
      }
      Material frameMat = ItemShopTypes.material(gui, "frame-material", Material.GRAY_STAINED_GLASS_PANE);
      ItemStack frame = ItemShopItems.fromSection(null, frameMat, " ", List.of());
      List<Integer> frameSlots = gui.getIntegerList("frame-slots");
      if (frameSlots.isEmpty()) {
         frameSlots = List.of(20, 21, 22, 23, 24, 31);
      }
      for (int slot : frameSlots) {
         if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, frame.clone());
         }
      }
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.countdown"), "countdown");
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.previous"), "previous");
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.next"), "next");
      if (limitedTab) {
         ConfigurationSection back = gui.getConfigurationSection("items.back");
         if (back == null) {
            this.placeBackButton(player, inventory, gui);
         } else {
            this.placeUtility(player, inventory, back, "back");
         }
      } else {
         this.placeUtility(player, inventory, gui.getConfigurationSection("items.limited"), "limited");
      }
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.get-tokens"), "tokens");
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.balance"), "balance");
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.my-cosmetics"), "cosmetics");
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.my-tags"), "tags");
      this.placeUtility(player, inventory, gui.getConfigurationSection("items.close"), "close");
      List<Integer> content = this.contentSlots(gui);
      List<ItemShopTypes.Cosmetic> hats = this.module.browserHats(limitedTab);
      int start = Math.max(0, page) * content.size();
      for (int i = 0; i < content.size() && start + i < hats.size(); i++) {
         int slot = content.get(i);
         if (slot < 0 || slot >= inventory.getSize()) {
            continue;
         }
         inventory.setItem(slot, this.listingIcon(player, hats.get(start + i)));
      }
   }

   private List<Integer> contentSlots(ConfigurationSection gui) {
      List<Integer> content = gui.getIntegerList("content-slots");
      if (!content.isEmpty()) {
         return content;
      }
      List<Integer> slots = new ArrayList<>();
      for (int i = 0; i < 18; i++) {
         slots.add(i);
      }
      return slots;
   }

   private int pageCount(ConfigurationSection gui, boolean limitedTab) {
      int per = Math.max(1, this.contentSlots(gui).size());
      int count = this.module.browserHats(limitedTab).size();
      return Math.max(1, (count + per - 1) / per);
   }

   private void placeBackButton(Player player, Inventory inventory, ConfigurationSection gui) {
      int slot = 40;
      ConfigurationSection limited = gui.getConfigurationSection("items.limited");
      if (limited != null) {
         slot = limited.getInt("slot", 40);
      }
      ItemStack item = ItemShopItems.fromSection(
         null,
         Material.FLOWER_BANNER_PATTERN,
         "&#ff0000&lBACK",
         List.of(
            "&8Description",
            "",
            "&#ff0000Information:",
            "&#ff0000| &#ff0000Click Here,&f To",
            "&#ff0000| &fGo Back to the Main &#ff0000Shop",
            "",
            "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Navigate"
         )
      );
      ItemShopItems.mark(item, this.clickKey, "back");
      if (slot >= 0 && slot < inventory.getSize()) {
         inventory.setItem(slot, item);
      }
   }

   private ItemStack listingIcon(Player player, ItemShopTypes.Cosmetic cosmetic) {
      ItemShopTypes.Rarity rarity = this.module.catalog().rarity(cosmetic.rarityId());
      boolean glow = rarity != null && rarity.glow();
      ItemStack icon = ItemShopItems.cosmeticIcon(this.module.catalog(), cosmetic, this.module.hatDisplays(), glow);
      long price = this.module.catalog().price(cosmetic);
      String color = colourOf(cosmetic, rarity == null ? "&f" : rarity.color());
      String rarityName = rarity == null ? cosmetic.rarityId().toUpperCase(Locale.ROOT) : ItemShopItems.clean(rarity.displayName());
      List<String> template = this.module.shop().getStringList("shop-item.lore");
      List<String> lore = new ArrayList<>();
      String status = this.module.status(player, cosmetic);
      String typeName = "tag".equals(cosmetic.type()) ? "tag" : "hat";
      String description = cosmetic.lore().isEmpty()
         ? ("tag".equals(cosmetic.type()) ? "Equip this tag in chat." : "Equip this hat as a helmet.")
         : ItemShopItems.clean(cosmetic.lore().get(0));
      for (String line : template) {
         lore.add(ItemShopItems.apply(
            line,
            "{name}", cosmetic.displayName(),
            "%item%", cosmetic.displayName(),
            "{item}", cosmetic.displayName(),
            "{description}", description,
            "{rarity}", rarityName,
            "{price}", String.valueOf(price),
            "%price%", String.valueOf(price),
            "{currency}", this.module.currencyName(),
            "{status}", status,
            "{color}", color,
            "%colour%", color,
            "{colour}", color,
            "{type}", typeName
         ));
      }
      ItemShopItems.lore(icon, lore);
      ItemShopItems.mark(icon, this.clickKey, cosmetic.type() + ":" + cosmetic.id());
      return icon;
   }

   private static String colourOf(ItemShopTypes.Cosmetic cosmetic, String fallback) {
      String name = cosmetic == null ? null : cosmetic.displayName();
      if (name == null) {
         return fallback;
      }
      String text = name.stripLeading();
      if (text.length() >= 8 && text.regionMatches(true, 0, "&#", 0, 2)) {
         String hex = text.substring(2, 8);
         if (hex.chars().allMatch(ch -> Character.digit(ch, 16) >= 0)) {
            return "&#" + hex;
         }
      }
      if (text.length() >= 14 && text.regionMatches(true, 0, "&x", 0, 2)) {
         String hex = text.substring(0, 14);
         if (hex.matches("(?i)&x(&[0-9a-f]){6}")) {
            return hex;
         }
      }
      if (text.length() >= 2 && text.charAt(0) == '&') {
         return text.substring(0, 2);
      }
      return fallback == null || fallback.isBlank() ? "&f" : fallback;
   }

   private void paintNeighbors(Inventory inventory, int slot, Material border, ItemStack filler) {
      int[] offsets = {-9, -1, 1, 9};
      for (int offset : offsets) {
         int nearby = slot + offset;
         if (nearby < 0 || nearby >= inventory.getSize()) {
            continue;
         }
         if (offset == -1 && slot % 9 == 0) {
            continue;
         }
         if (offset == 1 && slot % 9 == 8) {
            continue;
         }
         ItemStack current = inventory.getItem(nearby);
         if (current != null && current.getType() == filler.getType()) {
            ItemStack pane = new ItemStack(border);
            ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
               meta.displayName(Text.c(" "));
               pane.setItemMeta(meta);
            }
            inventory.setItem(nearby, pane);
         }
      }
   }

   private void placeUtility(Player player, Inventory inventory, ConfigurationSection section, String action) {
      if (section == null || !section.getBoolean("enabled", true)) {
         return;
      }
      int slot = section.getInt("slot", 0);
      if (slot < 0 || slot >= inventory.getSize()) {
         return;
      }
      TokenService tokens = this.module.plugin().modules().tokens();
      long balance = tokens == null ? 0L : tokens.getBalance(player.getUniqueId());
      String time = this.module.formatCountdown();
      String season = this.module.activeSeason();
      String name = ItemShopItems.apply(
         section.getString("name", " "),
         "{time}", time,
         "%time%", time,
         "{season}", season,
         "%season%", season,
         "{balance}", String.valueOf(balance),
         "{currency}", this.module.currencyName(),
         "{symbol}", this.module.currencySymbol()
      );
      List<String> lore = new ArrayList<>();
      for (String line : section.getStringList("lore")) {
         lore.add(ItemShopItems.apply(
            line,
            "{time}", time,
            "%time%", time,
            "{season}", season,
            "%season%", season,
            "{balance}", String.valueOf(balance),
            "{currency}", this.module.currencyName(),
            "{symbol}", this.module.currencySymbol()
         ));
      }
      ItemStack item = ItemShopItems.fromSection(section, Material.CLOCK, name, lore);
      ItemShopItems.mark(item, this.clickKey, action);
      inventory.setItem(slot, item);
   }

   void tickOpenShops() {
      ConfigurationSection countdown = this.module.shop().getConfigurationSection("gui.shop.items.countdown");
      if (countdown == null || !countdown.getBoolean("enabled", true)) {
         return;
      }
      for (Player player : Bukkit.getOnlinePlayers()) {
         Inventory open = player.getOpenInventory().getTopInventory();
         if (open.getHolder() instanceof ItemShopModule.ShopHolder
            || TrackedInventories.lookup(open, ItemShopModule.ShopHolder.class) != null
            || open.getHolder() instanceof ItemShopModule.LimitedHolder
            || TrackedInventories.lookup(open, ItemShopModule.LimitedHolder.class) != null) {
            this.placeUtility(player, open, countdown, "countdown");
         }
      }
   }

   void refreshOpenShops() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         Inventory open = player.getOpenInventory().getTopInventory();
         ItemShopModule.LimitedHolder limited = open.getHolder() instanceof ItemShopModule.LimitedHolder held
            ? held
            : TrackedInventories.lookup(open, ItemShopModule.LimitedHolder.class);
         if (limited != null) {
            this.openLimited(player, limited.page);
         } else if (open.getHolder() instanceof ItemShopModule.ShopHolder shopHolder) {
            this.openShop(player, shopHolder.page);
         } else {
            ItemShopModule.ShopHolder tracked = TrackedInventories.lookup(open, ItemShopModule.ShopHolder.class);
            if (tracked != null) {
               this.openShop(player, tracked.page);
            }
         }
      }
   }

   void openConfirm(Player player, String type, String id) {
      this.openConfirm(player, type, id, "shop");
   }

   void openConfirm(Player player, String type, String id, String back) {
      YamlConfiguration shop = this.module.shop();
      if (!shop.getBoolean("gui.confirm.enabled", true)) {
         this.module.tryPurchase(player, type, id);
         this.returnFromConfirm(player, back);
         return;
      }
      ItemShopTypes.Cosmetic cosmetic = this.module.catalog().cosmetic(type, id);
      if (cosmetic == null) {
         return;
      }
      ConfigurationSection gui = shop.getConfigurationSection("gui.confirm");
      int rows = Math.max(1, Math.min(6, gui.getInt("rows", 3)));
      ItemShopModule.ConfirmHolder holder = new ItemShopModule.ConfirmHolder();
      holder.type = type;
      holder.id = id;
      holder.back = back;
      Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.c(gui.getString("title", "&8Confirm Purchase")));
      holder.inventory = inventory;
      TrackedInventories.track(inventory, holder);
      Material filler = ItemShopTypes.material(gui, "filler-material", Material.BLACK_STAINED_GLASS_PANE);
      ItemStack fill = ItemShopItems.fromSection(gui, filler, " ", List.of());
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, fill.clone());
      }
      ItemShopTypes.Rarity rarity = this.module.catalog().rarity(cosmetic.rarityId());
      ItemStack icon = ItemShopItems.cosmeticIcon(this.module.catalog(), cosmetic, this.module.hatDisplays(), rarity != null && rarity.glow());
      ItemShopItems.lore(icon, cosmetic.lore());
      inventory.setItem(gui.getInt("item-slot", 13), icon);
      long price = this.module.catalog().price(cosmetic);
      this.placeConfirmButton(inventory, gui.getConfigurationSection("confirm"), "confirm", price);
      this.placeConfirmButton(inventory, gui.getConfigurationSection("cancel"), "cancel", price);
      player.openInventory(inventory);
   }

   private void placeConfirmButton(Inventory inventory, ConfigurationSection section, String action, long price) {
      if (section == null) {
         return;
      }
      int slot = section.getInt("slot", 0);
      List<String> lore = new ArrayList<>();
      for (String line : section.getStringList("lore")) {
         lore.add(ItemShopItems.apply(line, "{price}", String.valueOf(price), "{currency}", this.module.currencyName()));
      }
      ItemStack item = ItemShopItems.fromSection(section, Material.LIME_STAINED_GLASS_PANE, section.getString("name", action), lore);
      ItemShopItems.mark(item, this.clickKey, action);
      if (slot >= 0 && slot < inventory.getSize()) {
         inventory.setItem(slot, item);
      }
   }

   void openCosmetics(Player player, String filter, int page) {
      YamlConfiguration shop = this.module.shop();
      ConfigurationSection gui = shop.getConfigurationSection("gui.cosmetics");
      int rows = Math.max(1, Math.min(6, gui.getInt("rows", 6)));
      ItemShopModule.CosmeticsHolder holder = new ItemShopModule.CosmeticsHolder();
      holder.filter = filter;
      holder.page = Math.max(0, page);
      Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.c(gui.getString("title", "&8My Cosmetics")));
      holder.inventory = inventory;
      TrackedInventories.track(inventory, holder);
      Material filler = ItemShopTypes.material(gui, "filler-material", Material.BLACK_STAINED_GLASS_PANE);
      ItemStack fill = ItemShopItems.fromSection(gui, filler, " ", List.of());
      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, fill.clone());
      }
      this.placeFilter(inventory, gui.getConfigurationSection("filter-all"), "filter:all");
      this.placeFilter(inventory, gui.getConfigurationSection("filter-hats"), "filter:hats");
      this.placeFilter(inventory, gui.getConfigurationSection("filter-tags"), "filter:tags");
      this.placeFilter(inventory, gui.getConfigurationSection("unequip-all"), "unequip-all");
      this.placeFilter(inventory, gui.getConfigurationSection("previous"), "prev");
      this.placeFilter(inventory, gui.getConfigurationSection("next"), "next");
      this.placeFilter(inventory, gui.getConfigurationSection("back"), "back");
      List<ItemShopTypes.Cosmetic> items = this.ownedList(player, filter);
      List<Integer> content = gui.getIntegerList("content-slots");
      if (content.isEmpty()) {
         content = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25);
      }
      int start = holder.page * content.size();
      for (int i = 0; i < content.size() && start + i < items.size(); i++) {
         ItemShopTypes.Cosmetic cosmetic = items.get(start + i);
         ItemShopTypes.Rarity rarity = this.module.catalog().rarity(cosmetic.rarityId());
         ItemStack icon = ItemShopItems.cosmeticIcon(this.module.catalog(), cosmetic, this.module.hatDisplays(), rarity != null && rarity.glow());
         boolean equipped = cosmetic.id().equalsIgnoreCase(this.module.equipped(player.getUniqueId(), cosmetic.type()));
         List<String> lore = new ArrayList<>(cosmetic.lore());
         lore.add("");
         if (rarity != null) {
            lore.add(rarity.loreLine());
         }
         if (equipped) {
            lore.add(gui.getString("equipped-lore", "&#94FF00| &fEquipped"));
            lore.add(gui.getString("click-unequip", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Unequip"));
         } else {
            lore.add(gui.getString("click-equip", "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To Equip"));
         }
         ItemShopItems.lore(icon, lore);
         ItemShopItems.mark(icon, this.clickKey, "own:" + cosmetic.type() + ":" + cosmetic.id());
         inventory.setItem(content.get(i), icon);
      }
      player.openInventory(inventory);
   }

   private void placeFilter(Inventory inventory, ConfigurationSection section, String action) {
      if (section == null) {
         return;
      }
      int slot = section.getInt("slot", 0);
      ItemStack item = ItemShopItems.fromSection(
         section,
         Material.CHEST,
         section.getString("name", action),
         section.getStringList("lore")
      );
      ItemShopItems.mark(item, this.clickKey, action);
      if (slot >= 0 && slot < inventory.getSize()) {
         inventory.setItem(slot, item);
      }
   }

   private List<ItemShopTypes.Cosmetic> ownedList(Player player, String filter) {
      List<ItemShopTypes.Cosmetic> list = new ArrayList<>();
      if (!"tags".equals(filter)) {
         for (String id : this.module.ownedIds(player.getUniqueId(), "hat")) {
            ItemShopTypes.Cosmetic cosmetic = this.module.catalog().hat(id);
            if (cosmetic != null) {
               list.add(cosmetic);
            }
         }
      }
      if (!"hats".equals(filter)) {
         for (String id : this.module.ownedIds(player.getUniqueId(), "tag")) {
            ItemShopTypes.Cosmetic cosmetic = this.module.catalog().tag(id);
            if (cosmetic != null) {
               list.add(cosmetic);
            }
         }
      }
      list.sort(Comparator.comparing((ItemShopTypes.Cosmetic c) -> {
         ItemShopTypes.Rarity rarity = this.module.catalog().rarity(c.rarityId());
         return rarity == null ? 0 : rarity.sortOrder();
      }).reversed().thenComparing(ItemShopTypes.Cosmetic::id));
      return list;
   }

   void openLimited(Player player, int page) {
      YamlConfiguration shop = this.module.shop();
      ConfigurationSection gui = shop.getConfigurationSection("gui.shop");
      int rows = gui == null ? 6 : Math.max(1, Math.min(6, gui.getInt("rows", 6)));
      int pages = gui == null ? 1 : this.pageCount(gui, true);
      ItemShopModule.LimitedHolder holder = new ItemShopModule.LimitedHolder();
      holder.page = Math.max(0, Math.min(page, pages - 1));
      String title = gui == null ? "&8Item Shop" : gui.getString("title", "&8Item Shop");
      Inventory inventory = Bukkit.createInventory(holder, rows * 9, Text.c(title));
      holder.inventory = inventory;
      TrackedInventories.track(inventory, holder);
      if (gui != null) {
         this.paintShop(player, inventory, gui, holder.page, true);
      }
      player.openInventory(inventory);
      ItemShopItems.play(player, shop.getConfigurationSection("sounds.click"));
   }

   private void purchaseAction(Player player, String action, String back) {
      if (!action.contains(":")) {
         return;
      }
      String[] parts = action.split(":", 2);
      if (this.module.owns(player.getUniqueId(), parts[0], parts[1])) {
         this.module.msg(player, "already-owned");
         return;
      }
      this.openConfirm(player, parts[0], parts[1], back);
   }

   private void returnFromConfirm(Player player, String back) {
      if ("limited".equals(back)) {
         this.openLimited(player, 0);
      } else {
         this.openShop(player);
      }
   }

   void handleClick(Player player, InventoryClickEvent event) {
      String action = ItemShopItems.read(event.getCurrentItem(), this.clickKey);
      if (action == null || action.isBlank()) {
         return;
      }
      ItemShopItems.play(player, this.module.shop().getConfigurationSection("sounds.click"));
      Object holder = TrackedInventories.lookup(event.getView().getTopInventory());
      if (holder == null) {
         holder = event.getView().getTopInventory().getHolder();
      }
      if (holder instanceof ItemShopModule.ShopHolder shopHolder) {
         switch (action) {
            case "close" -> player.closeInventory();
            case "cosmetics" -> {
               player.closeInventory();
               player.performCommand(this.module.shop().getString("gui.shop.items.my-cosmetics.command", "wardrobe"));
            }
            case "tags" -> {
               player.closeInventory();
               player.performCommand(this.module.shop().getString("gui.shop.items.my-tags.command", "tags"));
            }
            case "tokens" -> {
               String command = this.module.shop().getString("gui.shop.items.get-tokens.command", "tokenmethods");
               player.closeInventory();
               player.performCommand(command);
            }
            case "previous" -> this.openShop(player, shopHolder.page - 1);
            case "next" -> this.openShop(player, shopHolder.page + 1);
            case "limited" -> this.openLimited(player, 0);
            case "countdown", "balance" -> {
            }
            default -> this.purchaseAction(player, action, "shop");
         }
         return;
      }
      if (holder instanceof ItemShopModule.LimitedHolder limited) {
         switch (action) {
            case "back" -> this.openShop(player);
            case "previous", "prev" -> this.openLimited(player, limited.page - 1);
            case "next" -> this.openLimited(player, limited.page + 1);
            case "cosmetics" -> {
               player.closeInventory();
               player.performCommand(this.module.shop().getString("gui.shop.items.my-cosmetics.command", "wardrobe"));
            }
            case "tags" -> {
               player.closeInventory();
               player.performCommand(this.module.shop().getString("gui.shop.items.my-tags.command", "tags"));
            }
            case "tokens" -> {
               String command = this.module.shop().getString("gui.shop.items.get-tokens.command", "tokenmethods");
               player.closeInventory();
               player.performCommand(command);
            }
            case "countdown", "balance", "limited" -> {
            }
            default -> this.purchaseAction(player, action, "limited");
         }
         return;
      }
      if (holder instanceof ItemShopModule.ConfirmHolder confirm) {
         if (action.equals("confirm")) {
            this.module.tryPurchase(player, confirm.type, confirm.id);
            this.returnFromConfirm(player, confirm.back);
         } else if (action.equals("cancel")) {
            this.returnFromConfirm(player, confirm.back);
         }
         return;
      }
      if (holder instanceof ItemShopModule.CosmeticsHolder cosmetics) {
         if (action.equals("back")) {
            this.openShop(player);
            return;
         }
         if (action.equals("unequip-all")) {
            this.module.unequipAll(player);
            this.openCosmetics(player, cosmetics.filter, cosmetics.page);
            return;
         }
         if (action.equals("prev")) {
            this.openCosmetics(player, cosmetics.filter, Math.max(0, cosmetics.page - 1));
            return;
         }
         if (action.equals("next")) {
            this.openCosmetics(player, cosmetics.filter, cosmetics.page + 1);
            return;
         }
         if (action.startsWith("filter:")) {
            this.openCosmetics(player, action.substring("filter:".length()), 0);
            return;
         }
         if (action.startsWith("own:")) {
            String[] parts = action.split(":", 3);
            if (parts.length == 3) {
               String equipped = this.module.equipped(player.getUniqueId(), parts[1]);
               if (parts[2].equalsIgnoreCase(equipped)) {
                  this.module.unequip(player, parts[1]);
               } else {
                  this.module.equip(player, parts[1], parts[2]);
               }
               this.openCosmetics(player, cosmetics.filter, cosmetics.page);
            }
         }
      }
   }
}

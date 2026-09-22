package com.sharded.core.modules.leaderboards;

import com.sharded.core.modules.stats.StatsModule;
import com.sharded.core.setup.gui.GuiHelper;
import com.sharded.core.setup.gui.GuiHolder;
import com.sharded.core.setup.util.ItemBuilder;
import com.sharded.core.setup.util.NumberUtil;
import com.sharded.core.setup.util.SoundUtil;
import com.sharded.core.setup.util.TextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

final class LeaderboardGuiHandler {
   private static final List<HubIcon> HUB = List.of(
         new HubIcon("kills", 20, Material.NETHERITE_SWORD),
         new HubIcon("playtime", 21, Material.CLOCK),
         new HubIcon("totems", 22, Material.TOTEM_OF_UNDYING),
         new HubIcon("killstreaks", 23, Material.MACE),
         new HubIcon("teams", 24, Material.PURPLE_BANNER),
         new HubIcon("deaths", 30, Material.SKELETON_SKULL),
         new HubIcon("tokens", 32, Material.LIGHT_BLUE_BUNDLE)
   );

   private record HubIcon(String board, int slot, Material material) {
   }
   private final LeaderboardsModule module;
   private final LeaderboardService service;
   private final YamlConfiguration cfg;
   private final NamespacedKey headKey;

   LeaderboardGuiHandler(LeaderboardsModule module, LeaderboardService service, YamlConfiguration cfg) {
      this.module = module;
      this.service = service;
      this.cfg = cfg;
      this.headKey = new NamespacedKey(module.plugin(), "lb-player");
   }

   void openHub(Player player) {
      Inventory inventory = GuiHelper.create("leaderboards-main",
            this.cfg.getString("gui.hub-title", this.cfg.getString("gui-title", "&8Leaderboards")),
            this.cfg.getInt("gui-rows", this.cfg.getInt("rows", 5)));
      GuiHelper.fillGlass(inventory);
      String name = player.getName();
      List<String> headLore = new ArrayList<>();
      for (String line : this.cfg.getStringList("main.head-lore")) {
         headLore.add(line.replace("%playername%", name).replace("%player%", name)
               .replace("%rank%", "-"));
      }
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta) head.getItemMeta();
      if (meta != null) {
         meta.setOwningPlayer(player);
         meta.displayName(TextUtil.itemComponent(this.cfg.getString("main.head-name", "&#0098FF%playername%")
               .replace("%playername%", name)));
         List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
         for (String line : headLore) {
            lore.add(TextUtil.itemComponent(line));
         }
         meta.lore(lore);
         head.setItemMeta(meta);
      }
      inventory.setItem(this.cfg.getInt("main.head-slot", 13), head);
      for (HubIcon icon : this.hubIcons()) {
         this.placeCategory(inventory, icon, player);
      }
      player.openInventory(inventory);
      SoundUtil.play(player, this.cfg.getString("sound-open", "block.note_block.pling"));
   }

   void openBoard(Player player, String type) {
      String board = this.normalize(type);
      if (board == null) {
         return;
      }
      String path = "boards." + board + ".";
      String color = this.cfg.getString(path + "color", "&#FFFFFF");
      String symbol = this.cfg.getString(path + "symbol", "☀");
      String title = this.cfg.getString(path + "title", board);
      String statistic = this.cfg.getString(path + "statistic", title);
      int rows = Math.max(3, this.cfg.getInt("board-rows", 6));
      String guiTitle = this.cfg.getString(path + "gui-title",
            this.cfg.getString("gui.board-title." + board,
                  this.cfg.getString("board-title", "&8Leaderboards | %statistic%")));
      Inventory inventory = GuiHelper.create("leaderboards-" + board,
            guiTitle.replace("%statistic%", statistic).replace("%title%", title).replace("%color%", color), rows);
      GuiHelper.fillDarkGlass(inventory);
      this.paintBoard(inventory, player, board, color, symbol, statistic);
      player.openInventory(inventory);
      SoundUtil.play(player, this.cfg.getString("sound-open", "block.note_block.pling"));
   }

   void tickOpenBoards() {
      boolean rolled = this.service.ensureFresh();
      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            continue;
         }
         String id = holder.getId();
         if ("leaderboards-main".equals(id)) {
            continue;
         }
         if (!id.startsWith("leaderboards-")) {
            continue;
         }
         String board = this.normalize(id.substring("leaderboards-".length()));
         if (board == null) {
            continue;
         }
         Inventory inventory = player.getOpenInventory().getTopInventory();
         String path = "boards." + board + ".";
         String color = this.cfg.getString(path + "color", "&#FFFFFF");
         String symbol = this.cfg.getString(path + "symbol", "☀");
         String statistic = this.cfg.getString(path + "statistic", this.cfg.getString(path + "title", board));
         if (rolled) {
            this.paintBoard(inventory, player, board, color, symbol, statistic);
         }
      }
   }

   private void paintBoard(Inventory inventory, Player player, String board, String color, String symbol, String statistic) {
      List<LeaderboardService.Entry> entries = this.service.entries(board);
      int[] slots = this.cfg.getIntegerList("board-slots").stream().mapToInt(Integer::intValue).toArray();
      if (slots.length == 0) {
         slots = new int[]{19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
      }
      ItemStack glass = ItemBuilder.darkGlassPane();
      for (int slot : slots) {
         if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, glass);
         }
      }
      int limit = Math.min(entries.size(), slots.length);
      for (int i = 0; i < limit; i++) {
         inventory.setItem(slots[i], this.buildHead(entries.get(i), board, color, symbol, i + 1));
      }
      this.placeYou(inventory, player, board, color, symbol, statistic);
      inventory.setItem(this.cfg.getInt("back-slot", 45), GuiHelper.backButton("Leaderboards"));
      this.placePositionHeads(inventory, entries, board, color, symbol);
   }

   private void placePositionHeads(Inventory inventory, List<LeaderboardService.Entry> entries, String board, String color, String symbol) {
      var section = this.cfg.getConfigurationSection("position-heads");
      if (section == null) {
         return;
      }
      for (String key : section.getKeys(false)) {
         int slot;
         try {
            slot = Integer.parseInt(key);
         } catch (NumberFormatException ignored) {
            continue;
         }
         int rank = section.getInt(key, 0);
         if (rank < 1 || rank > entries.size() || slot < 0 || slot >= inventory.getSize()) {
            continue;
         }
         inventory.setItem(slot, this.buildHead(entries.get(rank - 1), board, color, symbol, rank));
      }
   }

   private void placeHubRefreshClock(Inventory inventory) {
      int slot = this.cfg.getInt("hub-refresh-slot", 41);
      if (slot < 0 || slot >= inventory.getSize()) {
         return;
      }
      String color = this.cfg.getString("boards.tokens.color", "&x&5&C&9&4&F&C");
      String time = formatCountdown(this.service.remainingMillis());
      List<String> lore = new ArrayList<>();
      for (String line : this.cfg.getStringList("hub-refresh-lore")) {
         lore.add(line.replace("%color%", color)
               .replace("%time%", time)
               .replace("%timer%", time));
      }
      if (lore.isEmpty()) {
         lore.addAll(List.of(
               "&8Leaderboard",
               "",
               color + "Information:",
               color + "| &fRankings refresh in",
               color + "| &f" + time
         ));
      }
      inventory.setItem(slot, ItemBuilder.of(Material.CLOCK)
            .name(this.cfg.getString("hub-refresh-name", color + "&lREFRESHES IN")
                  .replace("%color%", color)
                  .replace("%time%", time))
            .lore(lore)
            .hideExtras()
            .build());
   }

   private void placeRefreshClock(Inventory inventory, String color, String statistic) {
      int slot = this.cfg.getInt("refresh-slot", 13);
      if (slot < 0 || slot >= inventory.getSize()) {
         return;
      }
      String time = formatCountdown(this.service.remainingMillis());
      List<String> lore = new ArrayList<>();
      for (String line : this.cfg.getStringList("refresh-lore")) {
         lore.add(line.replace("%color%", color)
               .replace("%statistic%", statistic)
               .replace("%time%", time)
               .replace("%timer%", time));
      }
      inventory.setItem(slot, ItemBuilder.of(Material.CLOCK)
            .name(this.cfg.getString("refresh-name", "%color%&lMost %statistic%")
                  .replace("%color%", color)
                  .replace("%statistic%", statistic)
                  .replace("%time%", time))
            .lore(lore)
            .hideExtras()
            .build());
   }

   static String formatCountdown(long millis) {
      long total = Math.max(0L, millis / 1000L);
      long hours = total / 3600L;
      long minutes = total % 3600L / 60L;
      long seconds = total % 60L;
      if (hours > 0L) {
         return hours + "h " + minutes + "m " + seconds + "s";
      }
      if (minutes > 0L) {
         return minutes + "m " + seconds + "s";
      }
      return seconds + "s";
   }

   void openBoard(Player player, String type, int page) {
      this.openBoard(player, type);
   }

   void openStats(Player player, UUID uuid) {
      StatsModule stats = this.module.plugin().modules().get(StatsModule.class);
      if (stats != null) {
         stats.openStatsFor(player, Bukkit.getOfflinePlayer(uuid));
      }
   }

   void handleClick(Player player, InventoryClickEvent event) {
      if (!(event.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
         return;
      }
      String id = holder.getId();
      if (!id.startsWith("leaderboards")) {
         return;
      }
      event.setCancelled(true);
      if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
         return;
      }
      if ("leaderboards-main".equals(id)) {
         for (HubIcon icon : this.hubIcons()) {
            if (event.getRawSlot() == icon.slot()) {
               this.openBoard(player, icon.board());
               return;
            }
         }
         return;
      }
      if (id.startsWith("leaderboards-")) {
         if (event.getRawSlot() == this.cfg.getInt("refresh-slot", 13)) {
            return;
         }
         if (event.getRawSlot() == this.cfg.getInt("back-slot", 45)) {
            this.openHub(player);
            return;
         }
         ItemStack clicked = event.getCurrentItem();
         if (clicked == null || !(clicked.getItemMeta() instanceof SkullMeta meta)) {
            return;
         }
         String uuidStr = meta.getPersistentDataContainer().get(this.headKey, PersistentDataType.STRING);
         if (uuidStr == null) {
            return;
         }
         player.closeInventory();
         this.openStats(player, UUID.fromString(uuidStr));
      }
   }

   private List<HubIcon> hubIcons() {
      List<HubIcon> icons = new ArrayList<>();
      var section = this.cfg.getConfigurationSection("boards");
      if (section == null) {
         return HUB;
      }
      for (String board : section.getKeys(false)) {
         int slot = this.cfg.getInt("boards." + board + ".slot", -1);
         if (slot < 0) {
            continue;
         }
         Material material = Material.matchMaterial(this.cfg.getString("boards." + board + ".material", "PAPER"));
         if (material == null) {
            material = Material.PAPER;
         }
         icons.add(new HubIcon(board, slot, material));
      }
      return icons.isEmpty() ? HUB : icons;
   }

   private void placeCategory(Inventory inventory, HubIcon icon, Player viewer) {
      String board = icon.board();
      int slot = icon.slot();
      if (slot < 0 || slot >= inventory.getSize()) {
         return;
      }
      String path = "boards." + board + ".";
      Material material = icon.material();
      String color = this.cfg.getString(path + "color", "&#FFFFFF");
      String symbol = this.cfg.getString(path + "symbol", "☀");
      String title = this.cfg.getString(path + "title", board.toUpperCase(Locale.ROOT));
      String statistic = this.cfg.getString(path + "statistic", board);
      String displayName = this.cfg.getString(path + "name", color + "&l" + title);
      List<String> configured = this.cfg.getStringList(path + "lore");
      List<String> lore = new ArrayList<>();
      List<String> template = configured.isEmpty() ? this.cfg.getStringList("category-lore") : configured;
      for (String line : template) {
         lore.add(line.replace("%color%", color).replace("%symbol%", symbol)
               .replace("%title%", title).replace("%statistic%", statistic)
               .replace("%score_label%", this.cfg.getString(path + "you-label", this.cfg.getString(path + "score-label", statistic)))
               .replace("%score%", this.formatScore(board, this.service.valueOf(board, viewer.getUniqueId())))
               .replace("%rank%", "-"));
      }
      inventory.setItem(slot, ItemBuilder.of(material).name(displayName).lore(lore).hideExtras().build());
   }

   private ItemStack buildHead(LeaderboardService.Entry entry, String board, String color, String symbol, int rank) {
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta) head.getItemMeta();
      if (meta != null) {
         OfflinePlayer offline = entry.uuid() == null ? null : Bukkit.getOfflinePlayer(entry.uuid());
         if (offline != null) {
            meta.setOwningPlayer(offline);
         }
         meta.displayName(TextUtil.itemComponent(color + entry.displayName()));
         String scoreLabel = this.cfg.getString("boards." + board + ".score-label", board);
         String scoreValue = this.formatScore(board, entry.value());
         List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
         for (String line : this.cfg.getStringList("entry-lore")) {
            lore.add(TextUtil.itemComponent(line
                  .replace("%player_name%", entry.displayName())
                  .replace("%player%", entry.displayName())
                  .replace("%color%", color)
                  .replace("%symbol%", symbol)
                  .replace("%rank%", String.valueOf(rank))
                  .replace("%score%", scoreValue)
                  .replace("%score_label%", scoreLabel)));
         }
         meta.lore(lore);
         if (entry.uuid() != null) {
            meta.getPersistentDataContainer().set(this.headKey, PersistentDataType.STRING, entry.uuid().toString());
         }
         head.setItemMeta(meta);
      }
      return head;
   }

   private void placeYou(Inventory inventory, Player player, String board, String color, String symbol, String statistic) {
      int slot = this.cfg.getInt("you-slot", 49);
      if (slot < 0 || slot >= inventory.getSize()) {
         return;
      }
      int rank = this.service.rankOf(board, player.getUniqueId());
      long value = this.service.valueOf(board, player.getUniqueId());
      String scoreLabel = this.cfg.getString("boards." + board + ".you-label",
            this.cfg.getString("boards." + board + ".score-label", statistic));
      String rankText = rank <= 0 ? this.cfg.getString("gui.unranked", "Unranked") : "#" + rank;
      String scoreValue = this.formatScore(board, value);
      List<String> lore = new ArrayList<>();
      for (String line : this.cfg.getStringList("you-lore")) {
         lore.add(line
               .replace("%color%", color)
               .replace("%symbol%", symbol)
               .replace("%rank%", rankText)
               .replace("%position%", rankText)
               .replace("%score%", scoreValue)
               .replace("%score_label%", scoreLabel)
               .replace("%statistic%", statistic)
               .replace("%player%", player.getName())
               .replace("%playername%", player.getName()));
      }
      ItemStack head = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta) head.getItemMeta();
      if (meta != null) {
         meta.setOwningPlayer(player);
         meta.displayName(TextUtil.itemComponent(
               this.cfg.getString("you-name", "%color%&lYOUR %score_label%")
                     .replace("%color%", color)
                     .replace("%score_label%", scoreLabel.toUpperCase(Locale.ROOT))
                     .replace("%statistic%", statistic)
                     .replace("%player%", player.getName())
                     .replace("%playername%", player.getName())));
         List<net.kyori.adventure.text.Component> components = new ArrayList<>();
         for (String line : lore) {
            components.add(TextUtil.itemComponent(line));
         }
         meta.lore(components);
         meta.getPersistentDataContainer().set(this.headKey, PersistentDataType.STRING, player.getUniqueId().toString());
         head.setItemMeta(meta);
      }
      inventory.setItem(slot, head);
   }

   private String formatScore(String board, long value) {
      return switch (board) {
         case "playtime" -> com.sharded.core.util.Text.formatPlaytime(Math.max(0L, value));
         default -> NumberUtil.formatComma(value);
      };
   }

   private String normalize(String raw) {
      String key = raw.toLowerCase(Locale.ROOT);
      return switch (key) {
         case "tokens", "token" -> "tokens";
         case "kills", "kill" -> "kills";
         case "deaths", "death" -> "deaths";
         case "killstreaks", "killstreak", "streak" -> "killstreaks";
         case "playtime", "time" -> "playtime";
         case "teams", "team" -> "teams";
         case "totems", "totem", "totempops", "totem_pops" -> "totems";
         case "duels", "duels_wins", "wins" -> "duels";
         default -> null;
      };
   }
}

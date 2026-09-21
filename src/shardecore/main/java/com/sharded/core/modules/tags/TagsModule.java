package com.sharded.core.modules.tags;

import com.sharded.core.ShardedCore;
import com.sharded.core.cosmetics.CosmeticDatabase;
import com.sharded.core.hook.LuckPermsHook;
import com.sharded.core.module.Module;
import com.sharded.core.util.BundleColorUtil;
import com.sharded.core.util.GradientUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class TagsModule extends Module implements CommandExecutor, TabCompleter {
   private static final int[] CONTENT = new int[]{
      10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43
   };
   private static final int[] OWNED_CONTENT = new int[]{
      10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34
   };
   private static final Map<UUID, TagsModule.Session> SESSIONS = new ConcurrentHashMap<>();
   private static final Set<UUID> CLICK_GUARD = ConcurrentHashMap.newKeySet();
   private final Map<String, TagsModule.TagDef> tags = new LinkedHashMap<>();
   private final Map<String, TagsModule.TagDef> limited = new LinkedHashMap<>();
   private TagsDatabase database;

   public TagsModule(ShardedCore plugin) {
      super(plugin, "tags");
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new TagsDatabase(this.plugin, this.moduleFolder());
      } catch (Exception ex) {
         this.plugin.getLogger().warning("[tags] Could not open tags.db: " + ex.getMessage());
      }
      this.reloadTags();
      this.grantCreatorToMediaGroup();
      this.registerCommand("tags", this);
      this.registerCommand("tag", this);
   }

   @Override
   protected void onDisable() {
      if (this.database != null) {
         this.database.close();
         this.database = null;
      }
   }

   private void grantCreatorToMediaGroup() {
      if (this.plugin.luckPerms() != null && this.plugin.luckPerms().isAvailable()) {
         this.plugin.luckPerms().runConsole("lp group media permission set sharded.tag.creator true");
      }
   }

   public static boolean isViewing(UUID uuid) {
      return uuid != null && SESSIONS.containsKey(uuid);
   }

   static boolean isCreatorTag(String id) {
      return TagIds.isCreatorTag(id);
   }

   public boolean owns(Player player, String tagId) {
      if (player == null || tagId == null) {
         return false;
      }
      TagsModule.TagDef tag = this.tags.get(tagId.toLowerCase(Locale.ROOT));
      if (tag == null) {
         tag = this.limited.get(tagId.toLowerCase(Locale.ROOT));
      }
      return tag != null && this.ownsTag(player, tag);
   }

   public boolean isUnlocked(UUID uuid, String tagId) {
      return this.database != null && uuid != null && tagId != null && this.database.isUnlocked(uuid, tagId);
   }

   public boolean unlock(Player player, String tagId) {
      return this.unlock(player == null ? null : player.getUniqueId(), tagId, player);
   }

   public boolean unlock(UUID uuid, String tagId, Player announceTo) {
      if (uuid == null || tagId == null || tagId.isBlank()) {
         return false;
      }
      String id = tagId.toLowerCase(Locale.ROOT);
      TagsModule.TagDef tag = this.tags.get(id);
      if (tag == null) {
         tag = this.limited.get(id);
      }
      if (tag == null) {
         return false;
      }
      if (this.database != null) {
         this.database.unlock(uuid, id);
      }
      Player online = announceTo != null ? announceTo : Bukkit.getPlayer(uuid);
      if (online != null && this.plugin.luckPerms() != null) {
         this.plugin.luckPerms().runConsole("lp user " + online.getName() + " permission set sharded.tag." + id + " true");
         if (tag.permission != null && !tag.permission.isBlank() && !tag.permission.equals("sharded.tag." + id)) {
            this.plugin.luckPerms().runConsole("lp user " + online.getName() + " permission set " + tag.permission + " true");
         }
      }
      return true;
   }

   private boolean ownsTag(Player player, TagsModule.TagDef tag) {
      if (this.database != null && this.database.isUnlocked(player.getUniqueId(), tag.id)) {
         return true;
      }
      return isCreatorTag(tag.id)
         ? this.hasMediaRank(player)
            || player.hasPermission("sharded.tag.creator")
            || player.hasPermission("sharded.tag.streamer")
            || player.hasPermission("sharded.tags.admin")
         : player.hasPermission(tag.permission)
            || player.hasPermission("sharded.tag." + tag.id)
            || player.hasPermission("eternaltags.tag." + tag.id)
            || player.hasPermission("sharded.tags.admin")
            || player.isOp();
   }

   private boolean hasMediaRank(Player player) {
      LuckPermsHook luckpermshook = this.plugin.luckPerms();
      if (luckpermshook != null && luckpermshook.isAvailable()) {
         if ("media".equalsIgnoreCase(luckpermshook.primaryGroup(player))) {
            return true;
         }

         if (luckpermshook.hasPermanentGroup(player.getUniqueId(), "media")) {
            return true;
         }

         if (luckpermshook.hasActiveTempGroup(player.getUniqueId(), "media")) {
            return true;
         }
      }

      return player.hasPermission("group.media");
   }

   private void reloadTags() {
      this.loadConfigs();
      this.migrateCreatorTag();
      this.tags.clear();
      this.limited.clear();
      this.loadSection(this.config.getConfigurationSection("tags"), this.tags, false);
      this.loadSection(this.config.getConfigurationSection("limited"), this.limited, true);
      this.loadSection(this.config.getConfigurationSection("limited-tags"), this.limited, true);
      this.applyEquippedTagDisplays();
   }

   private void migrateCreatorTag() {
      boolean flag = false;
      if (this.config.getConfigurationSection("tags.streamer") != null && this.config.getConfigurationSection("tags.creator") == null) {
         this.config.set("tags.creator.permission", "sharded.tag.creator");
         this.config.set("tags.creator.hidden-unless-owned", true);
         flag = true;
      }

      if (this.config.getConfigurationSection("tags.streamer") != null) {
         this.config.set("tags.streamer", null);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 20) {
         this.config.set("tags.creator.tag", "&7[&#FF0067&lCREATOR&7]");
         this.config.set("tags.creator.name", "CREATOR");
         this.config.set("tags.creator.color", "&#FF0067");
         this.config.set("tags.creator.permission", "sharded.tag.creator");
         this.config.set("tags.creator.hidden-unless-owned", true);
         this.config.set("config-version", 20);
         flag = true;
      }

      if (this.config.getInt("config-version", 0) < 21) {
         this.config.set("tags.skillissue.name", "SKILL ISSUE");
         this.config.set("tags.freekill.name", "FREE KILL");
         this.config.set("tags.cooked.name", "COOKED");
         this.config.set("tags.ping999.name", "PING 999");
         this.config.set("tags.lagabuser.name", "LAG ABUSER");
         this.config.set("tags.onehp.name", "1 HP");
         this.config.set("tags.missed.name", "MISSED");
         this.config.set("tags.lootgoblin.name", "LOOT GOBLIN");
         this.config.set("tags.downbad.name", "DOWN BAD");
         this.config.set("tags.profrunner.name", "PROFESSIONAL RUNNER");
         this.config.set("tags.creator.name", "CREATOR");
         this.config.set("tags.touchgrass.tag", "&7[&#7CB342&lTOUCH GRASS&7]");
         this.config.set("tags.touchgrass.name", "TOUCH GRASS");
         this.config.set("tags.touchgrass.color", "#7CB342");
         this.config.set("tags.touchgrass.permission", "sharded.tag.touchgrass");
         this.config.set("tags.touchgrass.description", "&#7CB342Description");
         this.config.set("config-version", 21);
         flag = true;
      }

      if (flag) {
         this.saveConfigFile();
      }
   }

   private void applyEquippedTagDisplays() {
      if (this.plugin.cosmetics() == null || this.plugin.cosmetics().database() == null) {
         return;
      }
      for (Player player : Bukkit.getOnlinePlayers()) {
         CosmeticDatabase.PlayerCosmetics cosmetics = this.plugin.cosmetics().database().get(player.getUniqueId());
         String id = cosmetics.tagId();
         if (id == null || id.isBlank()) {
            continue;
         }
         TagsModule.TagDef def = this.tags.get(id.toLowerCase(Locale.ROOT));
         if (def == null) {
            def = this.limited.get(id.toLowerCase(Locale.ROOT));
         }
         if (def != null) {
            this.plugin.cosmetics().setTag(player, def.id, def.display);
         }
      }
   }

   private void loadSection(ConfigurationSection section, Map<String, TagsModule.TagDef> target, boolean limitedFlag) {
      if (section != null) {
         int i = 10;

         for (String s : section.getKeys(false)) {
            ConfigurationSection configurationsection = section.getConfigurationSection(s);
            if (configurationsection != null) {
               String s1 = configurationsection.getString("name", s.toUpperCase(Locale.ROOT));
               String s2 = configurationsection.getString(
                  "tag", configurationsection.getString("tag-display", configurationsection.getString("display-name", ""))
               );
               String s3 = configurationsection.getString("gradient-from", configurationsection.getString("hex", null));
               String s4 = configurationsection.getString("gradient-to", configurationsection.getString("hex2", null));
               if ((s2 == null || s2.isBlank()) && s3 != null && s4 != null) {
                  String s6 = configurationsection.getString("label", s1);
                  s2 = "&7[" + GradientUtil.apply(s6, s3, s4) + "&7]";
               } else if ((s2 == null || s2.isBlank()) && s3 != null) {
                  String s5 = s3.replace("#", "");
                  s2 = "&7[&#" + s5 + "&l" + s1 + "&7]";
               } else if (s2 == null || s2.isBlank()) {
                  s2 = s;
               }

               Color color = BundleColorUtil.parse(configurationsection.getString("color", s2));
               if (color == null && s3 != null) {
                  color = BundleColorUtil.parse(s3);
               }

               if (color == null) {
                  color = BundleColorUtil.fromHex(this.config.getString("gui.default-color", "A370EE"));
               }

               int j = configurationsection.getInt("slot", i++);
               if (i == 17) {
                  i = 19;
               }

               if (i == 26) {
                  i = 28;
               }

               if (i == 35) {
                  i = 37;
               }

               target.put(
                  s.toLowerCase(Locale.ROOT),
                  new TagsModule.TagDef(
                     s.toLowerCase(Locale.ROOT),
                     s1,
                     s2,
                     configurationsection.getString("description", this.config.getString("gui.default-description", "&8Description")),
                     configurationsection.getString("permission", "sharded.tag." + s.toLowerCase(Locale.ROOT)),
                     j,
                     color,
                     limitedFlag,
                     configurationsection.getBoolean("hidden-unless-owned", isCreatorTag(s))
                  )
               );
            }
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("tags")) {
         if (args.length >= 3 && args[0].equalsIgnoreCase("limited")) {
            if (!sender.hasPermission("sharded.tags.admin")) {
               this.msg(sender, "no-permission");
               return true;
            } else {
               return this.createTag(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)), true);
            }
         } else if (sender instanceof Player player1) {
            if (!player1.hasPermission("sharded.tags.use") && !player1.isOp()) {
               this.msg(player1, "no-permission");
               return true;
            } else {
               try {
                  this.openMenu(player1, false, 0);
               } catch (Throwable throwable) {
                  this.plugin.getLogger().severe("[tags] Failed to open GUI for " + player1.getName() + ": " + throwable.getMessage());
                  throwable.printStackTrace();
                  player1.sendMessage(Text.c("&#FF0000&lERROR &8▷ &fCould not open tags. Check console."));
               }

               return true;
            }
         } else {
            this.msg(sender, "players-only");
            return true;
         }
      } else if (sender instanceof Player player) {
         if (args.length == 0) {
            if (!player.hasPermission("sharded.tags.use") && !player.isOp()) {
               this.msg(player, "no-permission");
               return true;
            } else {
               try {
                  this.openMenu(player, false, 0);
               } catch (Throwable throwable1) {
                  this.plugin.getLogger().severe("[tags] Failed to open GUI for " + player.getName() + ": " + throwable1.getMessage());
                  throwable1.printStackTrace();
                  player.sendMessage(Text.c("&#FF0000&lERROR &8▷ &fCould not open tags. Check console."));
               }

               return true;
            }
         } else {
            String s1 = args[0].toLowerCase(Locale.ROOT);
            if (s1.equals("create") && args.length >= 3) {
               if (!sender.hasPermission("sharded.tags.admin")) {
                  this.msg(sender, "no-permission");
                  return true;
               } else {
                  return this.createTag(sender, args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)), false);
               }
            } else if (s1.equals("set") && args.length >= 2) {
               return this.equip(player, args[1].toLowerCase(Locale.ROOT));
            } else if ((s1.equals("remove") || s1.equals("delete")) && args.length >= 2) {
               if (!sender.hasPermission("sharded.tags.admin")) {
                  this.msg(sender, "no-permission");
                  return true;
               } else {
                  String s2 = args[1].toLowerCase(Locale.ROOT);
                  if (this.config.getConfigurationSection("tags." + s2) != null) {
                     this.config.set("tags." + s2, null);
                  } else {
                     if (this.config.getConfigurationSection("limited." + s2) == null) {
                        this.msg(sender, "missing", "%name%", s2);
                        return true;
                     }

                     this.config.set("limited." + s2, null);
                  }

                  this.saveConfigFile();
                  this.reloadTags();
                  this.msg(sender, "removed", "%name%", s2);
                  return true;
               }
            } else if (!s1.equals("clear") && !s1.equals("reset")) {
               this.msg(sender, "usage");
               return true;
            } else {
               if (this.plugin.cosmetics() != null) {
                  this.plugin.cosmetics().clearTag(player);
               }

               this.msg(player, "cleared");
               return true;
            }
         }
      } else {
         this.msg(sender, "players-only");
         return true;
      }
   }

   private boolean createTag(CommandSender sender, String rawId, String tagText, boolean limitedFlag) {
      String s = rawId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
      if (!s.isBlank() && tagText != null && !tagText.isBlank()) {
         String s1 = (limitedFlag ? "limited." : "tags.") + s;
         this.config.set(s1 + ".tag", tagText);
         this.config.set(s1 + ".name", rawId.toUpperCase(Locale.ROOT));
         this.config.set(s1 + ".description", this.config.getString("gui.default-description", "&8Description"));
         this.saveConfigFile();
         this.reloadTags();
         this.msg(sender, limitedFlag ? "created-limited" : "created", "%name%", s, "%tag%", tagText);
         return true;
      } else {
         this.msg(sender, "invalid");
         return true;
      }
   }

   private void saveConfigFile() {
      try {
         this.config.save(new File(this.moduleFolder(), "config.yml"));
      } catch (Exception exception) {
      }
   }

   public void openOwned(Player player) {
      this.openMenu(player, false, 0, true);
   }

   private void openMenu(Player player, boolean limitedMenu, int page) {
      this.openMenu(player, limitedMenu, page, false);
   }

   private void openMenu(Player player, boolean limitedMenu, int page, boolean ownedOnly) {
      List<TagsModule.TagDef> list = new ArrayList<>();
      if (ownedOnly) {
         for (TagsModule.TagDef tagsmodule$tagdef : this.tags.values()) {
            if (this.ownsTag(player, tagsmodule$tagdef)) {
               list.add(tagsmodule$tagdef);
            }
         }
         for (TagsModule.TagDef tagsmodule$tagdef : this.limited.values()) {
            if (this.ownsTag(player, tagsmodule$tagdef)) {
               list.add(tagsmodule$tagdef);
            }
         }
      } else {
         for (TagsModule.TagDef tagsmodule$tagdef : (limitedMenu ? this.limited : this.tags).values()) {
            if (!tagsmodule$tagdef.hiddenUnlessOwned || this.ownsTag(player, tagsmodule$tagdef)) {
               list.add(tagsmodule$tagdef);
            }
         }
      }

      int i1 = ownedOnly ? 6 : Math.max(1, Math.min(6, this.config.getInt("gui.rows", limitedMenu ? 6 : 6)));
      String s = ownedOnly
         ? this.config.getString("gui.owned-title", "&#FCFF00&lYour Tags")
         : (limitedMenu ? this.config.getString("gui.limited-title", "Limited Tags") : this.config.getString("gui.title", "Name Tags"));
      TagsModule.Holder tagsmodule$holder = new TagsModule.Holder(limitedMenu, page, ownedOnly);
      Inventory inventory = Bukkit.createInventory(tagsmodule$holder, i1 * 9, Text.c(s));
      TrackedInventories.track(inventory, tagsmodule$holder);
      ItemStack itemstack = new ItemBuilder(this.material(this.config.getString("filler.material"), Material.BLACK_STAINED_GLASS_PANE))
         .name(this.config.getString("filler.name", " "))
         .build();

      for (int i = 0; i < inventory.getSize(); i++) {
         inventory.setItem(i, itemstack);
      }

      int[] slots = ownedOnly ? OWNED_CONTENT : CONTENT;
      int j1 = slots.length;
      int j = Math.max(0, (list.size() + j1 - 1) / j1 - 1);
      page = Math.max(0, Math.min(page, j));
      tagsmodule$holder.page = page;
      int k = page * j1;

      for (int l = 0; l < j1 && k + l < list.size(); l++) {
         TagsModule.TagDef tagsmodule$tagdef1 = list.get(k + l);
         inventory.setItem(slots[l], this.tagItem(player, tagsmodule$tagdef1));
         tagsmodule$holder.actions.put(slots[l], "equip:" + tagsmodule$tagdef1.id);
      }

      if (ownedOnly && list.isEmpty()) {
         inventory.setItem(
            22,
            new ItemBuilder(Material.NAME_TAG)
               .name("&#FCFF00&lNO TAGS")
               .lore("&8Description", "", "&#FCFF00| &fYou do not own any tags yet.")
               .build()
         );
      }

      if (this.config.getBoolean("clear.enabled", true) && !limitedMenu) {
         int k1 = ownedOnly ? 49 : this.config.getInt("clear.slot", 4);
         inventory.setItem(
            k1,
            new ItemBuilder(this.material(this.config.getString("clear.material"), Material.BARRIER))
               .name(this.config.getString("clear.name", "&#FF0000&lCLEAR TAG"))
               .lore(this.config.getStringList("clear.lore"))
               .build()
         );
         tagsmodule$holder.actions.put(k1, "clear");
      }

      if (this.config.getBoolean("extra.enabled", true) && !limitedMenu && !ownedOnly) {
         int l1 = this.config.getInt("extra.slot", 49);
         inventory.setItem(
            l1,
            new ItemBuilder(this.material(this.config.getString("extra.material"), Material.CLOCK))
               .name(this.config.getString("extra.name", "&#FFBA00&lLIMITED TAGS"))
               .lore(this.config.getStringList("extra.lore"))
               .build()
         );
         tagsmodule$holder.actions.put(l1, "limited");
      }

      if (limitedMenu) {
         int i2 = this.config.getInt("limited-back.slot", this.config.getInt("back.slot", 49));
         inventory.setItem(i2, new ItemBuilder(Material.ARROW).name("&#FFBA00&lBack").build());
         tagsmodule$holder.actions.put(i2, "back");
      }

      if (j > 0) {
         int j2 = ownedOnly ? 45 : this.config.getInt("previous.slot", 48);
         int k2 = ownedOnly ? 53 : this.config.getInt("next.slot", 50);
         if (page > 0) {
            inventory.setItem(j2, new ItemBuilder(Material.ARROW).name("&cPrevious").build());
            tagsmodule$holder.actions.put(j2, "page:" + (page - 1));
         }

         if (page < j) {
            inventory.setItem(k2, new ItemBuilder(Material.ARROW).name("&aNext").build());
            tagsmodule$holder.actions.put(k2, "page:" + (page + 1));
         }
      }

      this.play(player, "open");
      SESSIONS.put(player.getUniqueId(), new TagsModule.Session(limitedMenu, page, new LinkedHashMap<>(tagsmodule$holder.actions), TagMenuTitles.plain(s), ownedOnly));
      player.openInventory(inventory);
   }

   private ItemStack tagItem(Player player, TagsModule.TagDef tag) {
      boolean flag = this.ownsTag(player, tag);
      String s = BundleColorUtil.accentCode(tag.color);
      String s1 = flag ? this.config.getString("gui.status-owned", "&#94FF00&l&nOWNED") : this.config.getString("gui.status-locked", "&#FF0000&l&nLOCKED");
      String s2 = this.config.getString("gui.click-footer", "&eClick");
      List<String> list = new ArrayList<>();

      for (String s3 : this.config.getStringList("gui.lore")) {
         list.add(s3.replace("%color%", s).replace("%name%", tag.name).replace("%status%", s1).replace("%click%", s2).replace("%description%", tag.description));
      }

      String s4 = this.config.getString("gui.item-name", "%color%&l%name%").replace("%color%", s).replace("%name%", tag.name);
      Material material1 = this.material(this.config.getString("gui.item-material"), Material.BUNDLE);
      Material material = material1;
      if (this.config.getBoolean("gui.colored-bundles", true) && (material1 == Material.BUNDLE || material1.name().endsWith("_BUNDLE"))) {
         material = BundleColorUtil.bundleMaterial(tag.color);
      }

      ItemStack itemstack = new ItemBuilder(material).name(s4).lore(list).hideAll().build();
      if (this.config.getBoolean("gui.colored-bundles", true)) {
         BundleColorUtil.dye(itemstack, tag.color);
      }

      return itemstack;
   }

   private boolean equip(Player player, String id) {
      if ("streamer".equalsIgnoreCase(id)) {
         id = "creator";
      }

      TagsModule.TagDef tagsmodule$tagdef = this.tags.get(id);
      if (tagsmodule$tagdef == null) {
         tagsmodule$tagdef = this.limited.get(id);
      }

      if (tagsmodule$tagdef == null) {
         this.msg(player, "missing", "%name%", id);
         this.play(player, "error");
         return true;
      } else if (!this.ownsTag(player, tagsmodule$tagdef)) {
         this.msg(player, "locked", "%name%", tagsmodule$tagdef.name);
         this.play(player, "error");
         return true;
      } else if (this.plugin.cosmetics() == null) {
         this.plugin.getLogger().warning("[tags] Cosmetics is not ready; could not equip " + tagsmodule$tagdef.id);
         this.msg(player, "invalid");
         this.play(player, "error");
         return true;
      } else {
         this.plugin.cosmetics().setTag(player, tagsmodule$tagdef.id, tagsmodule$tagdef.display);
         this.msg(player, "set", "%tag%", tagsmodule$tagdef.display, "%name%", tagsmodule$tagdef.name);
         this.play(player, "equip");
         return true;
      }
   }

   public void handleMenuClick(Player player, int slot) {
      UUID uuid = player.getUniqueId();
      if (CLICK_GUARD.add(uuid)) {
         this.plugin.getServer().getScheduler().runTask(this.plugin, () -> CLICK_GUARD.remove(uuid));
         Map<Integer, String> map = null;
         TagsModule.Session tagsmodule$session = SESSIONS.get(uuid);
         if (tagsmodule$session != null) {
            map = tagsmodule$session.actions();
         } else {
            TagsModule.Holder tagsmodule$holder = this.holderOf(player.getOpenInventory().getTopInventory());
            if (tagsmodule$holder != null) {
               map = tagsmodule$holder.actions;
            }
         }

         if (map != null) {
            String s = map.get(slot);
            if (s != null) {
               this.play(player, "click");
               if (s.equals("clear")) {
                  if (this.plugin.cosmetics() != null) {
                     this.plugin.cosmetics().clearTag(player);
                  }

                  this.msg(player, "cleared");
                  player.closeInventory();
               } else if (s.equals("limited")) {
                  player.closeInventory();
                  this.openMenu(player, true, 0);
               } else if (s.equals("back")) {
                  player.closeInventory();
                  this.openMenu(player, false, 0);
               } else if (!s.startsWith("page:")) {
                  if (s.startsWith("equip:")) {
                     this.equip(player, s.substring(6));
                     player.closeInventory();
                  }
               } else {
                  int i = Integer.parseInt(s.substring(5));
                  boolean flag = tagsmodule$session != null && tagsmodule$session.limited();
                  boolean owned = tagsmodule$session != null && tagsmodule$session.owned();
                  if (tagsmodule$session == null) {
                     TagsModule.Holder tagsmodule$holder1 = this.holderOf(player.getOpenInventory().getTopInventory());
                     flag = tagsmodule$holder1 != null && tagsmodule$holder1.limited;
                     owned = tagsmodule$holder1 != null && tagsmodule$holder1.owned;
                  }

                  player.closeInventory();
                  this.openMenu(player, owned ? false : flag, i, owned);
               }
            }
         }
      }
   }

   private TagsModule.Holder holderOf(Inventory inventory) {
      if (inventory == null) {
         return null;
      } else {
         InventoryHolder inventoryholder = inventory.getHolder();
         if (inventoryholder instanceof TagsModule.Holder) {
            return (TagsModule.Holder)inventoryholder;
         } else {
            try {
               Object object = inventory.getClass().getMethod("getHolder", boolean.class).invoke(inventory, false);
               if (object instanceof TagsModule.Holder) {
                  return (TagsModule.Holder)object;
               }
            } catch (Throwable throwable) {
            }

            return TrackedInventories.lookup(inventory, TagsModule.Holder.class);
         }
      }
   }

   private boolean lockedMenu(Player player) {
      return this.holderOf(player.getOpenInventory().getTopInventory()) != null ? true : TagMenuTitles.isEquipMenu(plainTitle(player.getOpenInventory()));
   }

   static String plainTitle(InventoryView view) {
      if (view == null) {
         return "";
      } else {
         try {
            return TagMenuTitles.plain(PlainTextComponentSerializer.plainText().serialize(view.title()));
         } catch (Throwable throwable1) {
            try {
               return TagMenuTitles.plain(view.getTitle());
            } catch (Throwable throwable) {
               return "";
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void lockClicks(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         if (this.lockedMenu(player)) {
            event.setCancelled(true);
            event.setResult(Result.DENY);
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = false
   )
   public void onClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.lockedMenu(player)) {
         event.setCancelled(true);
         event.setResult(Result.DENY);
         player.updateInventory();
         if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
         }

         this.handleMenuClick(player, event.getSlot());
         return;
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = false
   )
   public void onDrag(InventoryDragEvent event) {
      if (event.getWhoClicked() instanceof Player player && this.lockedMenu(player)) {
         event.setCancelled(true);
         event.setResult(Result.DENY);
         player.updateInventory();
         return;
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      if (event.getPlayer() instanceof Player player) {
         TagsModule.Session tagsmodule$session = SESSIONS.get(player.getUniqueId());
         if (tagsmodule$session != null) {
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
               if (!player.isOnline()) {
                  SESSIONS.remove(player.getUniqueId());
               } else {
                  TagsModule.Session tagsmodule$session1 = SESSIONS.get(player.getUniqueId());
                  if (tagsmodule$session1 == tagsmodule$session) {
                     if (!this.stillHasTagsMenuOpen(player, tagsmodule$session)) {
                        SESSIONS.remove(player.getUniqueId());
                     }
                  }
               }
            }, 1L);
         }
      }
   }

   private boolean stillHasTagsMenuOpen(Player player, TagsModule.Session expected) {
      Inventory inventory = player.getOpenInventory().getTopInventory();
      if (inventory == null) {
         return false;
      } else {
         InventoryType inventorytype = inventory.getType();
         if (inventorytype == InventoryType.CRAFTING || inventorytype == InventoryType.CREATIVE || inventorytype == InventoryType.PLAYER) {
            return false;
         } else if (this.holderOf(inventory) != null) {
            return true;
         } else {
            String s = plainTitle(player.getOpenInventory());
            return expected != null && expected.title() != null && expected.title().equalsIgnoreCase(s) ? true : TagMenuTitles.isEquipMenu(s);
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      SESSIONS.remove(event.getPlayer().getUniqueId());
   }

   private void msg(CommandSender sender, String key, String... replacements) {
      String s = this.config.getString("messages." + key);
      if (s != null && !s.isBlank()) {
         String s1 = this.config.getString("prefix", this.config.getString("messages.prefix", ""));
         s = s.replace("%prefix%", s1);
         sender.sendMessage(Text.c(Text.apply(s, replacements)));
      } else {
         this.send(sender, key, replacements);
      }
   }

   private void play(Player player, String key) {
      if (this.config.getBoolean("sounds." + key + ".enabled", true)) {
         String s = this.config.getString("sounds." + key + ".sound", "ui.button.click");
         float f = (float)this.config.getDouble("sounds." + key + ".volume", 1.0);
         float f1 = (float)this.config.getDouble("sounds." + key + ".pitch", 1.0);

         try {
            player.playSound(player.getLocation(), Sound.valueOf(s.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT)), f, f1);
         } catch (IllegalArgumentException illegalargumentexception) {
            player.playSound(player.getLocation(), s, f, f1);
         }
      }
   }

   private Material material(String raw, Material fallback) {
      if (raw != null && !raw.isBlank()) {
         Material material = Material.matchMaterial(raw.toUpperCase(Locale.ROOT));
         return material == null ? fallback : material;
      } else {
         return fallback;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (command.getName().equalsIgnoreCase("tags") && args.length == 1) {
         return TabCompleteHelper.filter(args[0], "limited");
      } else if (command.getName().equalsIgnoreCase("tag") && args.length == 1) {
         return TabCompleteHelper.filter(args[0], "create", "set", "remove", "clear");
      } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
         List<String> list = new ArrayList<>();
         list.addAll(this.tags.keySet());
         list.addAll(this.limited.keySet());
         return TabCompleteHelper.filter(args[1], list);
      } else {
         return List.of();
      }
   }

   public static final class Holder implements InventoryHolder {
      private final boolean limited;
      private final boolean owned;
      private int page;
      private final Map<Integer, String> actions = new LinkedHashMap<>();

      private Holder(boolean limited, int page, boolean owned) {
         this.limited = limited;
         this.owned = owned;
         this.page = page;
      }

      public Inventory getInventory() {
         return null;
      }
   }

   private static record Session(boolean limited, int page, Map<Integer, String> actions, String title, boolean owned) {
   }

   private static record TagDef(
      String id, String name, String display, String description, String permission, int slot, Color color, boolean limited, boolean hiddenUnlessOwned
   ) {
   }
}

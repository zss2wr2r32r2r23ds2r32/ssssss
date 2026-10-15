package com.sharded.core.gui;

import com.sharded.core.util.BundleUtil;
import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.ItemsAdderHook;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class GuiMenu {
   private final String id;
   private final String title;
   private final int size;
   private final String openPermission;
   private final List<String> openCommands;
   private final Map<Integer, GuiMenu.GuiItem> itemsBySlot = new HashMap<>();
   private final boolean autoFill;
   private final Material fillerMaterial;
   private final String fillerName;
   private final GuiNavigation navigation;

   public GuiMenu(String id, YamlConfiguration yaml, GuiNavigation navigation) {
      this.id = id;
      this.navigation = navigation;
      this.title = yaml.getString("menu_title", id);
      this.size = Math.max(9, Math.min(54, yaml.getInt("size", 27)));
      this.openPermission = yaml.getString("open_permission", "");
      this.openCommands = yaml.getStringList("open_commands");
      ConfigurationSection configurationsection = yaml.getConfigurationSection("filler");
      this.autoFill = configurationsection == null ? true : configurationsection.getBoolean("auto-fill", true);
      Material material = Material.BLACK_STAINED_GLASS_PANE;
      if (configurationsection != null) {
         Material material1 = Material.matchMaterial(configurationsection.getString("material", "BLACK_STAINED_GLASS_PANE").toUpperCase(Locale.ROOT));
         if (material1 != null) {
            material = material1;
         }
      }

      this.fillerMaterial = material;
      this.fillerName = configurationsection != null ? configurationsection.getString("name", " ") : " ";
      this.loadItems(yaml.getConfigurationSection("items"));
   }

   private void loadItems(ConfigurationSection section) {
      if (section != null) {
         for (String s : section.getKeys(false)) {
            ConfigurationSection configurationsection = section.getConfigurationSection(s);
            if (configurationsection != null) {
               String s1 = GuiNavigation.resolveNavType(s, configurationsection);
               boolean flag = configurationsection.getBoolean("nav-override", false);
               String s2 = configurationsection.getString("material", "STONE");
               if (s1 != null && this.navigation != null && (flag || !configurationsection.contains("material"))) {
                  s2 = this.navigation.section(s1) != null ? this.navigation.section(s1).getString("material", s2) : s2;
               }

               ItemStack itemstack = HeadUtil.parse(s2);
               if (itemstack == null) {
                  itemstack = ItemsAdderHook.parseItem(s2);
               }

               if (itemstack == null) {
                  Material material = Material.matchMaterial(s2.toUpperCase(Locale.ROOT));
                  itemstack = new ItemStack(material == null ? Material.STONE : material);
               }

               boolean flag1 = HeadUtil.isViewerHeadMaterial(s2);
               String s3 = configurationsection.getString("display_name", " ");
               if (s1 != null
                  && this.navigation != null
                  && this.navigation.section(s1) != null
                  && (flag || !configurationsection.contains("display_name") || s3.isBlank())) {
                  s3 = this.navigation.displayName(s1, null);
               }

               List<String> list = new ArrayList<>();
               if (s1 == null
                  || this.navigation == null
                  || this.navigation.section(s1) == null
                  || !flag && !configurationsection.getStringList("lore").isEmpty()) {
                  for (String s4 : configurationsection.getStringList("lore")) {
                     list.add(s4);
                  }
               } else {
                  list.addAll(this.navigation.lore(s1, null));
               }

               if (!flag1) {
                  itemstack = new ItemBuilder(itemstack).name(s3).lore(list).hideAll().build();
               } else {
                  itemstack = new ItemBuilder(itemstack).hideAll().build();
               }

               List<String> list2 = configurationsection.getStringList("left_click_commands");
               List<String> list3 = configurationsection.getStringList("click_commands");
               if (list3.isEmpty()) {
                  list3 = list2;
               }

               if (list3.isEmpty()) {
                  list3 = configurationsection.getStringList("right_click_commands");
               }

               String s5 = configurationsection.getString("permission", "");
               List<Integer> list1 = new ArrayList<>();
               if (configurationsection.contains("slot")) {
                  list1.add(configurationsection.getInt("slot"));
               }

               if (configurationsection.contains("slots")) {
                  list1.addAll(configurationsection.getIntegerList("slots"));
               }

               for (int i : list1) {
                  this.itemsBySlot.put(i, new GuiMenu.GuiItem(i, itemstack, s3, list, list2, list3, s5, flag1));
               }
            }
         }
      }
   }

   public String id() {
      return this.id;
   }

   public List<String> openCommands() {
      return this.openCommands;
   }

   public GuiMenu.GuiItem itemAt(int slot) {
      return this.itemsBySlot.get(slot);
   }

   public void open(Player player, GuiManager manager, Map<String, String> extraPlaceholders) {
      if (!this.openPermission.isEmpty() && !player.hasPermission(resolvePermission(this.openPermission))) {
         manager.message(player, manager.noPermissionMessage(), true);
      } else {
         GuiMenu.OpenGuiHolder guimenu$openguiholder = new GuiMenu.OpenGuiHolder(this.id);
         Inventory inventory = Bukkit.createInventory(guimenu$openguiholder, this.size, Text.c(apply(this.title, player, extraPlaceholders, manager)));
         guimenu$openguiholder.inventory = inventory;
         TrackedInventories.track(inventory, guimenu$openguiholder);

         for (GuiMenu.GuiItem guimenu$guiitem : this.itemsBySlot.values()) {
            ItemStack itemstack = this.applyItem(guimenu$guiitem, player, extraPlaceholders, manager);
            BundleUtil.stripMenuTooltip(itemstack);
            inventory.setItem(guimenu$guiitem.slot(), itemstack);
         }

         if (this.autoFill) {
            ItemStack itemstack1 = new ItemBuilder(this.fillerMaterial).name(this.fillerName).hideAll().build();

            for (int i = 0; i < this.size; i++) {
               ItemStack itemstack2 = inventory.getItem(i);
               if (itemstack2 == null || itemstack2.getType().isAir()) {
                  inventory.setItem(i, itemstack1.clone());
               }
            }
         }

         player.openInventory(inventory);
         manager.runCommands(player, this.openCommands, extraPlaceholders);
      }
   }

   private ItemStack applyItem(GuiMenu.GuiItem item, Player player, Map<String, String> extra, GuiManager manager) {
      ItemStack itemstack = item.display().clone();
      if (item.viewerHead()) {
         itemstack = HeadUtil.applyViewer(itemstack, player);
      }

      ItemMeta itemmeta = itemstack.getItemMeta();
      if (itemmeta == null) {
         return itemstack;
      } else {
         if (item.rawName() != null && !item.rawName().isBlank()) {
            itemmeta.displayName(Text.c(apply(item.rawName(), player, extra, manager)));
         }

         if (item.rawLore() != null && !item.rawLore().isEmpty()) {
            List<Component> list = new ArrayList<>();

            for (String s : item.rawLore()) {
               list.add(Text.c(apply(s, player, extra, manager)));
            }

            itemmeta.lore(list);
         }

         itemstack.setItemMeta(itemmeta);
         return itemstack;
      }
   }

   private Component applyComponent(Component component, Player player, Map<String, String> extra, GuiManager manager) {
      String s = LegacyComponentSerializer.legacySection().serialize(component);
      return Text.c(apply(s, player, extra, manager));
   }

   private static String resolvePermission(String permission) {
      return permission.startsWith("sharded.") ? permission : "sharded." + permission;
   }

   public static String apply(String input, Player player, Map<String, String> extra, GuiManager manager) {
      if (input == null) {
         return "";
      } else {
         String s = input.replace("%player_name%", player.getName()).replace("%player%", player.getName());
         s = manager.applyPlaceholders(player, s);
         if (extra != null) {
            for (Entry<String, String> entry : extra.entrySet()) {
               s = s.replace("%" + entry.getKey() + "%", (CharSequence)(entry.getValue() == null ? "" : entry.getValue()));
            }
         }

         return s;
      }
   }

   public static record GuiItem(
      int slot,
      ItemStack display,
      String rawName,
      List<String> rawLore,
      List<String> leftClickCommands,
      List<String> clickCommands,
      String permission,
      boolean viewerHead
   ) {
   }

   public static final class OpenGuiHolder implements InventoryHolder {
      public final String menuId;
      private Inventory inventory;

      public OpenGuiHolder(String menuId) {
         this.menuId = menuId;
      }

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}

package com.sharded.core.modules.armortrims;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.BundleUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

public final class ArmorTrimsModule extends Module implements CommandExecutor {
   private int patternSlot = 10;
   private int armorSlot = 13;
   private int materialSlot = 16;
   private int confirmSlot = 22;
   private int guiSize = 27;
   private Material fillerMaterial = Material.BLACK_STAINED_GLASS_PANE;
   private String fillerName = "&r";
   private Material confirmMaterial = Material.LIME_DYE;
   private Material patternDisplayMaterial = Material.PAPER;
   private boolean useTemplateIcon = true;
   private String patternDisplayName = "&#0083FF&l%pattern%";
   private String materialDisplayName = "&#0083FF&l%material%";
   private boolean patternGlow = true;
   private boolean materialGlow = true;
   private Material patternFallback = Material.PAPER;
   private Material materialFallback = Material.EMERALD;
   private Map<String, Material> materialIcons = Map.of();
   private Map<String, Material> patternIcons = Map.of();

   public ArmorTrimsModule(ShardedCore plugin) {
      super(plugin, "armortrims");
   }

   @Override
   protected void onEnable() {
      this.reloadGuiSettings();
      this.registerCommand("armortrims", this);
   }

   private void reloadGuiSettings() {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("gui");
      this.guiSize = Math.max(9, Math.min(54, this.config.getInt("gui.size", 27)));
      if (configurationsection != null) {
         ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection("slots");
         if (configurationsection1 != null) {
            this.patternSlot = configurationsection1.getInt("pattern", 10);
            this.armorSlot = configurationsection1.getInt("armor", 13);
            this.materialSlot = configurationsection1.getInt("material", 16);
            this.confirmSlot = configurationsection1.getInt("confirm", 22);
         }

         ConfigurationSection configurationsection2 = configurationsection.getConfigurationSection("filler");
         if (configurationsection2 != null) {
            this.fillerMaterial = this.parseMaterial(configurationsection2.getString("material"), Material.BLACK_STAINED_GLASS_PANE);
            this.fillerName = configurationsection2.getString("name", "&r");
         }

         ConfigurationSection configurationsection3 = configurationsection.getConfigurationSection("pattern-item");
         if (configurationsection3 != null) {
            this.patternDisplayMaterial = this.parseMaterial(configurationsection3.getString("material"), Material.PAPER);
            this.useTemplateIcon = configurationsection3.getBoolean("use-template-icon", true);
            this.patternDisplayName = configurationsection3.getString("display-name", this.patternDisplayName);
            this.patternGlow = configurationsection3.getBoolean("glow", true);
         }

         ConfigurationSection configurationsection4 = configurationsection.getConfigurationSection("material-item");
         if (configurationsection4 != null) {
            this.materialDisplayName = configurationsection4.getString("display-name", this.materialDisplayName);
            this.materialGlow = configurationsection4.getBoolean("glow", true);
         }

         ConfigurationSection configurationsection5 = configurationsection.getConfigurationSection("confirm");
         if (configurationsection5 != null) {
            this.confirmMaterial = this.parseMaterial(configurationsection5.getString("material"), Material.LIME_CONCRETE);
         }
      }

      this.patternFallback = this.parseMaterial(this.config.getString("pattern-icon-fallback"), Material.PAPER);
      this.materialFallback = this.parseMaterial(this.config.getString("material-icon-fallback"), Material.EMERALD);
      this.materialIcons = this.loadIconMap("material-icons");
      this.patternIcons = this.loadIconMap("pattern-icons");
   }

   private Map<String, Material> loadIconMap(String path) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection(path);
      if (configurationsection == null) {
         return Map.of();
      } else {
         Map<String, Material> map = new HashMap<>();

         for (String s : configurationsection.getKeys(false)) {
            Material material = this.parseMaterial(configurationsection.getString(s), null);
            if (material != null) {
               map.put(s.toLowerCase(Locale.ROOT), material);
            }
         }

         return Map.copyOf(map);
      }
   }

   private Material parseMaterial(String name, Material fallback) {
      if (name != null && !name.isBlank()) {
         Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
         return material != null ? material : fallback;
      } else {
         return fallback;
      }
   }

   private Sound parseSound(String path, Sound fallback) {
      String s = this.config.getString(path);
      if (s != null && !s.isBlank()) {
         try {
            return Sound.valueOf(s.trim().toUpperCase(Locale.ROOT));
         } catch (IllegalArgumentException illegalargumentexception) {
            return fallback;
         }
      } else {
         return fallback;
      }
   }

   private float soundFloat(String path, float fallback) {
      return (float)this.config.getDouble(path, (double)fallback);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         String permission = this.config.getString("permission", "sharded.armortrims.use");
         if (!player.hasPermission(permission)) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            this.open(player);
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void open(Player player) {
      this.reloadGuiSettings();
      ArmorTrimsModule.TrimHolder armortrimsmodule$trimholder = new ArmorTrimsModule.TrimHolder();
      Inventory inventory = Bukkit.createInventory(armortrimsmodule$trimholder, this.guiSize, Text.c(this.config.getString("title", "&8Armor Trim Station")));
      armortrimsmodule$trimholder.inventory = inventory;
      TrackedInventories.track(inventory, armortrimsmodule$trimholder);
      this.loadOptions(armortrimsmodule$trimholder);
      this.render(armortrimsmodule$trimholder);
      player.openInventory(inventory);
      player.playSound(
         player.getLocation(),
         this.parseSound("sounds.open", Sound.BLOCK_SMITHING_TABLE_USE),
         this.soundFloat("sounds.open-volume", 0.6F),
         this.soundFloat("sounds.open-pitch", 1.2F)
      );
   }

   private void loadOptions(ArmorTrimsModule.TrimHolder holder) {
      holder.patterns.clear();
      holder.materials.clear();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("filters");
      boolean flag = configurationsection == null || configurationsection.getBoolean("exclude-netherite", true);
      List<String> list = configurationsection != null
         ? configurationsection.getStringList("excluded-patterns")
         : this.config.getStringList("excluded-patterns");
      List<String> list1 = configurationsection != null
         ? configurationsection.getStringList("excluded-materials")
         : this.config.getStringList("excluded-materials");
      Registry<TrimPattern> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);

      for (TrimPattern trimpattern : registry) {
         NamespacedKey namespacedkey = registry.getKey(trimpattern);
         if (namespacedkey != null) {
            String s = namespacedkey.getKey().toLowerCase(Locale.ROOT);
            if ((!flag || !s.contains("netherite")) && !s.equals("silence") && !list.contains(s)) {
               holder.patterns.add(trimpattern);
            }
         }
      }

      Registry<TrimMaterial> registry1 = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);

      for (TrimMaterial trimmaterial : registry1) {
         NamespacedKey namespacedkey1 = registry1.getKey(trimmaterial);
         if (namespacedkey1 != null) {
            String s1 = namespacedkey1.getKey().toLowerCase(Locale.ROOT);
            if ((!flag || !s1.contains("netherite")) && !list1.contains(s1)) {
               holder.materials.add(trimmaterial);
            }
         }
      }
   }

   private void render(ArmorTrimsModule.TrimHolder holder) {
      Inventory inventory = holder.inventory;
      ItemStack itemstack = new ItemBuilder(this.fillerMaterial).name(this.fillerName).build();
      ItemStack itemstack1 = inventory.getItem(this.armorSlot);

      for (int i = 0; i < inventory.getSize(); i++) {
         if (i != this.armorSlot) {
            inventory.setItem(i, itemstack);
         }
      }

      inventory.setItem(this.armorSlot, itemstack1);
      Registry<TrimPattern> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_PATTERN);
      if (!holder.patterns.isEmpty()) {
         TrimPattern trimpattern = holder.patterns.get(holder.patternIndex);
         NamespacedKey namespacedkey = registry.getKey(trimpattern);
         String s = this.registryLabel(namespacedkey);
         String s1 = Text.apply(this.messages.getString("pattern-name", this.patternDisplayName), "%pattern%", s);
         ItemStack itemstack2 = new ItemBuilder(this.patternDisplayMaterial(namespacedkey))
            .name(s1)
            .lore(this.rawList("pattern-lore", new String[0]))
            .glow(this.patternGlow)
            .hideAll()
            .build();
         BundleUtil.forceCustomTooltip(itemstack2);
         inventory.setItem(this.patternSlot, itemstack2);
      }

      Registry<TrimMaterial> registry1 = RegistryAccess.registryAccess().getRegistry(RegistryKey.TRIM_MATERIAL);
      if (!holder.materials.isEmpty()) {
         TrimMaterial trimmaterial = holder.materials.get(holder.materialIndex);
         NamespacedKey namespacedkey1 = registry1.getKey(trimmaterial);
         String s2 = this.registryLabel(namespacedkey1);
         String s3 = Text.apply(this.messages.getString("material-name", this.materialDisplayName), "%material%", s2);
         ItemStack itemstack3 = new ItemBuilder(this.materialIcon(namespacedkey1))
            .name(s3)
            .lore(this.rawList("material-lore", new String[0]))
            .glow(this.materialGlow)
            .hideAll()
            .build();
         BundleUtil.forceCustomTooltip(itemstack3);
         inventory.setItem(this.materialSlot, itemstack3);
      }

      ItemStack itemstack4 = new ItemBuilder(this.confirmMaterial)
         .name(this.raw("confirm-name", new String[0]))
         .lore(this.rawList("confirm-lore", new String[0]))
         .hideAll()
         .build();
      inventory.setItem(this.confirmSlot, itemstack4);
   }

   private Material patternDisplayMaterial(NamespacedKey key) {
      if (this.useTemplateIcon && key != null) {
         Material material = this.patternIcon(key);
         if (material != Material.PAPER) {
            return material;
         }
      }

      Material material1 = key != null ? this.patternIcons.get(key.getKey().toLowerCase(Locale.ROOT)) : null;
      return material1 != null ? material1 : this.patternDisplayMaterial;
   }

   private String registryLabel(NamespacedKey key) {
      return key == null ? "Unknown" : key.getKey().replace('_', ' ').toUpperCase(Locale.ROOT);
   }

   private Material patternIcon(NamespacedKey key) {
      if (key != null) {
         Material material = this.patternIcons.get(key.getKey().toLowerCase(Locale.ROOT));
         if (material != null) {
            return material;
         }

         Material material1 = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_ARMOR_TRIM_SMITHING_TEMPLATE");
         if (material1 != null) {
            return material1;
         }
      }

      return this.patternFallback;
   }

   private Material materialIcon(NamespacedKey key) {
      if (key == null) {
         return this.materialFallback;
      } else {
         Material material = this.materialIcons.get(key.getKey().toLowerCase(Locale.ROOT));
         if (material != null) {
            return material;
         } else {
            material = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT));
            if (material != null) {
               return material;
            } else {
               material = Material.getMaterial(key.getKey().toUpperCase(Locale.ROOT) + "_INGOT");
               return material != null ? material : this.materialFallback;
            }
         }
      }
   }

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      ArmorTrimsModule.TrimHolder armortrimsmodule$trimholder = TrackedInventories.lookup(event.getView().getTopInventory(), ArmorTrimsModule.TrimHolder.class);
      if (armortrimsmodule$trimholder != null) {
         if (event.getWhoClicked() instanceof Player player) {
            int j = event.getRawSlot();
            int topSize = event.getView().getTopInventory().getSize();
            if (j < topSize) {
               if (j == this.armorSlot) {
                  ItemStack itemstack2 = event.getCursor();
                  if (itemstack2 != null && !itemstack2.getType().isAir() && !this.isArmor(itemstack2)) {
                     event.setCancelled(true);
                     this.send(player, "not-armor", new String[0]);
                  } else {
                     Bukkit.getScheduler().runTask(this.plugin, () -> this.applyTrim(armortrimsmodule$trimholder));
                  }
               } else {
                  event.setCancelled(true);
                  if (j == this.patternSlot && !armortrimsmodule$trimholder.patterns.isEmpty()) {
                     armortrimsmodule$trimholder.patternIndex = (armortrimsmodule$trimholder.patternIndex + 1) % armortrimsmodule$trimholder.patterns.size();
                     player.playSound(
                        player.getLocation(),
                        this.parseSound("sounds.cycle", Sound.UI_BUTTON_CLICK),
                        this.soundFloat("sounds.cycle-volume", 0.5F),
                        this.soundFloat("sounds.pattern-pitch", 1.4F)
                     );
                     this.applyTrim(armortrimsmodule$trimholder);
                  } else if (j == this.materialSlot && !armortrimsmodule$trimholder.materials.isEmpty()) {
                     armortrimsmodule$trimholder.materialIndex = (armortrimsmodule$trimholder.materialIndex + 1) % armortrimsmodule$trimholder.materials.size();
                     player.playSound(
                        player.getLocation(),
                        this.parseSound("sounds.cycle", Sound.UI_BUTTON_CLICK),
                        this.soundFloat("sounds.cycle-volume", 0.5F),
                        this.soundFloat("sounds.material-pitch", 1.7F)
                     );
                     this.applyTrim(armortrimsmodule$trimholder);
                  } else if (j == this.confirmSlot) {
                     this.applyTrim(armortrimsmodule$trimholder);
                     this.send(player, "trim-applied", new String[0]);
                     player.playSound(
                        player.getLocation(),
                        this.parseSound("sounds.confirm", Sound.BLOCK_SMITHING_TABLE_USE),
                        this.soundFloat("sounds.confirm-volume", 1.0F),
                        this.soundFloat("sounds.confirm-pitch", 1.0F)
                     );
                  }
               }
            } else {
               if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                  event.setCancelled(true);
                  ItemStack itemstack = event.getCurrentItem();
                  ItemStack itemstack1 = armortrimsmodule$trimholder.inventory.getItem(this.armorSlot);
                  if (this.isArmor(itemstack) && (itemstack1 == null || itemstack1.getType().isAir())) {
                     armortrimsmodule$trimholder.inventory.setItem(this.armorSlot, itemstack.clone());
                     event.setCurrentItem(null);
                     this.applyTrim(armortrimsmodule$trimholder);
                  }
               }
            }
         }
      }
   }

   private void applyTrim(ArmorTrimsModule.TrimHolder holder) {
      ItemStack itemstack = holder.inventory.getItem(this.armorSlot);
      if (this.isArmor(itemstack) && !holder.patterns.isEmpty() && !holder.materials.isEmpty()) {
         ArmorMeta armormeta = (ArmorMeta)itemstack.getItemMeta();
         armormeta.setTrim(new ArmorTrim(holder.materials.get(holder.materialIndex), holder.patterns.get(holder.patternIndex)));
         itemstack.setItemMeta(armormeta);
         holder.inventory.setItem(this.armorSlot, itemstack);
         this.render(holder);
      } else {
         this.render(holder);
      }
   }

   private boolean isArmor(ItemStack item) {
      return item != null && !item.getType().isAir() && item.getItemMeta() instanceof ArmorMeta;
   }

   @EventHandler
   public void onDrag(InventoryDragEvent event) {
      if (TrackedInventories.lookup(event.getView().getTopInventory(), ArmorTrimsModule.TrimHolder.class) != null) {
         for (int i : event.getRawSlots()) {
            if (i < event.getView().getTopInventory().getSize() && i != this.armorSlot) {
               event.setCancelled(true);
               return;
            }
         }
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      ArmorTrimsModule.TrimHolder armortrimsmodule$trimholder = TrackedInventories.untrack(event.getInventory(), ArmorTrimsModule.TrimHolder.class);
      if (armortrimsmodule$trimholder == null && event.getInventory().getHolder() instanceof ArmorTrimsModule.TrimHolder held) {
         armortrimsmodule$trimholder = held;
      }
      if (armortrimsmodule$trimholder != null && event.getPlayer() instanceof Player player) {
         ItemStack itemstack = event.getInventory().getItem(this.armorSlot);
         event.getInventory().setItem(this.armorSlot, null);
         this.returnTrimItem(player, itemstack);
      }
   }

   private void returnTrimItem(Player player, ItemStack itemstack) {
      if (itemstack == null || itemstack.getType().isAir()) {
         return;
      }
      ItemStack returning = itemstack.clone();
      java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(returning);
      leftover.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
   }

   private final class TrimHolder implements InventoryHolder {
      private Inventory inventory;
      private int patternIndex;
      private int materialIndex;
      private final List<TrimPattern> patterns = new ArrayList<>();
      private final List<TrimMaterial> materials = new ArrayList<>();

      public Inventory getInventory() {
         return this.inventory;
      }
   }
}

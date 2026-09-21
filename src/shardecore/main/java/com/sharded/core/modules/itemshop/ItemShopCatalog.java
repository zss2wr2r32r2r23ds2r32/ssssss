package com.sharded.core.modules.itemshop;

import com.sharded.core.modules.wardrobe.HatCatalog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ItemShopCatalog {
   private final Map<String, ItemShopTypes.Rarity> rarities = new LinkedHashMap<>();
   private final Map<String, ItemShopTypes.Cosmetic> hats = new LinkedHashMap<>();
   private final Map<String, ItemShopTypes.Cosmetic> tags = new LinkedHashMap<>();

   public void load(YamlConfiguration raritiesYaml, YamlConfiguration hatsYaml, YamlConfiguration tagsYaml) {
      this.rarities.clear();
      this.hats.clear();
      this.tags.clear();
      ConfigurationSection rarityRoot = raritiesYaml.getConfigurationSection("rarities");
      if (rarityRoot != null) {
         List<String> keys = new ArrayList<>(rarityRoot.getKeys(false));
         keys.sort(Comparator.comparingInt(id -> rarityRoot.getInt(id + ".sort-order", 100)));
         for (String id : keys) {
            ConfigurationSection sec = rarityRoot.getConfigurationSection(id);
            if (sec != null) {
               this.rarities.put(
                  id.toLowerCase(Locale.ROOT),
                  new ItemShopTypes.Rarity(
                     id.toLowerCase(Locale.ROOT),
                     sec.getString("display-name", id),
                     sec.getString("color", "&f"),
                     sec.getString("gradient", sec.getString("color", "&f")),
                     Math.max(0, sec.getInt("weight", 1)),
                     sec.getLong("default-price-hat", 250L),
                     sec.getLong("default-price-tag", 200L),
                     ItemShopTypes.material(sec, "border-material", Material.PURPLE_STAINED_GLASS_PANE),
                     sec.getString("lore-line", id),
                     sec.getString("purchase-sound.sound", "entity.experience_orb.pickup"),
                     (float) sec.getDouble("purchase-sound.volume", 1.0),
                     (float) sec.getDouble("purchase-sound.pitch", 1.0),
                     sec.getBoolean("broadcast-on-purchase", false),
                     sec.getString("broadcast-message", ""),
                     sec.getBoolean("glow", false),
                     sec.getInt("sort-order", 100)
                  )
               );
            }
         }
      }
      this.loadCosmetics(hatsYaml.getConfigurationSection("hats"), "hat", this.hats);
      this.loadCosmetics(tagsYaml.getConfigurationSection("tags"), "tag", this.tags);
      this.importWardrobeHats();
   }

   public void importTagsFrom(YamlConfiguration tagsConfig) {
      ConfigurationSection root = tagsConfig.getConfigurationSection("tags");
      if (root == null) {
         return;
      }
      for (String id : root.getKeys(false)) {
         if (this.tags.containsKey(id.toLowerCase(Locale.ROOT))) {
            continue;
         }
         ConfigurationSection sec = root.getConfigurationSection(id);
         if (sec == null) {
            continue;
         }
         String name = sec.getString("name", id.toUpperCase(Locale.ROOT));
         String color = sec.getString("color", "&#A370EE");
         if (color.startsWith("#")) {
            color = "&" + color;
         } else if (!color.startsWith("&") && !color.startsWith("<")) {
            color = "&#" + color.replace("#", "");
         }
         boolean hidden = sec.getBoolean("hidden-unless-owned", false);
         this.tags.put(
            id.toLowerCase(Locale.ROOT),
            new ItemShopTypes.Cosmetic(
               "tag",
               id.toLowerCase(Locale.ROOT),
               color + "&l" + name,
               List.of("Show this tag in chat."),
               this.tagRarity(id),
               null,
               true,
               !hidden,
               false,
               sec.getString("permission", "sharded.tag." + id.toLowerCase(Locale.ROOT)),
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
               sec.getString("tag", ""),
               "before"
            )
         );
      }
   }

   private void importWardrobeHats() {
      for (HatCatalog.Seed seed : HatCatalog.all()) {
         if (HatCatalog.isRemoved(seed.id(), seed.itemsadderId())) {
            continue;
         }
         if (this.hats.containsKey(seed.id().toLowerCase(Locale.ROOT))) {
            continue;
         }
         this.hats.put(
            seed.id().toLowerCase(Locale.ROOT),
            new ItemShopTypes.Cosmetic(
               "hat",
               seed.id().toLowerCase(Locale.ROOT),
               seed.displayName(),
               List.of("Equip this hat as a helmet."),
               this.hatRarity(seed.price()),
               (long) seed.price(),
               true,
               true,
               false,
               "sharded.wardrobe." + seed.id(),
               List.of(),
               seed.itemsadderId(),
               seed.itemsadderId(),
               "",
               0.0,
               0.55,
               0.0,
               0.42F,
               0F,
               0F,
               0F,
               "",
               "before"
            )
         );
      }
   }

   private String hatRarity(int price) {
      if (price >= 1200) {
         return "legendary";
      }
      if (price >= 1000) {
         return "epic";
      }
      if (price >= 800) {
         return "rare";
      }
      return "common";
   }

   private String tagRarity(String id) {
      return switch (id.toLowerCase(Locale.ROOT)) {
         case "creator", "sharded", "one" -> "legendary";
         case "swordmummy", "hacker", "profrunner" -> "epic";
         case "p2w", "goty", "skillissue", "freekill", "cooked", "touchgrass" -> "rare";
         default -> "common";
      };
   }

   private void loadCosmetics(ConfigurationSection root, String type, Map<String, ItemShopTypes.Cosmetic> target) {
      if (root == null) {
         return;
      }
      for (String id : root.getKeys(false)) {
         ConfigurationSection sec = root.getConfigurationSection(id);
         if (sec == null) {
            continue;
         }
         ConfigurationSection display = sec.getConfigurationSection("display");
         Long price = sec.contains("price") ? sec.getLong("price") : null;
         String packId = sec.getString("itemsadder-id", sec.getString("material", type.equals("tag") ? "NAME_TAG" : "PAPER"));
         if ("hat".equals(type) && HatCatalog.isRemoved(id, packId)) {
            continue;
         }
         List<String> lore = new ArrayList<>(ItemShopItems.cleanLines(sec.getStringList("lore")));
         String description = ItemShopItems.clean(sec.getString("description", ""));
         if (!description.isEmpty()) {
            lore.add(0, description);
         } else if (lore.isEmpty()) {
            lore.add(type.equals("tag") ? "Show this tag in chat." : "Equip this hat as a helmet.");
         }
         if (HatCatalog.isRemoved(id, packId, sec.getString("itemsadder-id", packId))) {
            continue;
         }
         target.put(
            id.toLowerCase(Locale.ROOT),
            new ItemShopTypes.Cosmetic(
               type,
               id.toLowerCase(Locale.ROOT),
               ItemShopItems.clean(sec.getString("display-name", id)),
               lore,
               sec.getString("rarity", "common").toLowerCase(Locale.ROOT),
               price,
               sec.getBoolean("enabled", true),
               sec.getBoolean("in-rotation", true),
               sec.getBoolean("featured", false),
               sec.getString("permission", ""),
               sec.getStringList("servers"),
               packId,
               sec.getString("item_model", sec.getString("item-model", packId.contains(":") ? packId : "")),
               sec.getString("head_texture", sec.getString("head-texture", "")),
               display == null ? 0.0 : display.getDouble("offset.x", 0.0),
               display == null ? 0.55 : display.getDouble("offset.y", 0.55),
               display == null ? 0.0 : display.getDouble("offset.z", 0.0),
               display == null ? 0.4F : (float) display.getDouble("scale", 0.4),
               display == null ? 0F : (float) display.getDouble("rotation.x", 0.0),
               display == null ? 0F : (float) display.getDouble("rotation.y", 0.0),
               display == null ? 0F : (float) display.getDouble("rotation.z", 0.0),
               sec.getString("tag", ""),
               sec.getString("position", "before")
            )
         );
      }
   }

   public ItemShopTypes.Rarity rarity(String id) {
      return this.rarities.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
   }

   public Collection<ItemShopTypes.Rarity> rarities() {
      return this.rarities.values();
   }

   public ItemShopTypes.Cosmetic hat(String id) {
      return this.hats.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
   }

   public ItemShopTypes.Cosmetic tag(String id) {
      return this.tags.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
   }

   public ItemShopTypes.Cosmetic cosmetic(String type, String id) {
      return "tag".equalsIgnoreCase(type) ? this.tag(id) : this.hat(id);
   }

   public Collection<ItemShopTypes.Cosmetic> hats() {
      return this.hats.values();
   }

   public Collection<ItemShopTypes.Cosmetic> tags() {
      return this.tags.values();
   }

   public Collection<String> hatIds() {
      return this.hats.keySet();
   }

   public Collection<String> tagIds() {
      return this.tags.keySet();
   }

   public long price(ItemShopTypes.Cosmetic cosmetic) {
      if (cosmetic.price() != null) {
         return Math.max(0L, cosmetic.price());
      }
      ItemShopTypes.Rarity rarity = this.rarity(cosmetic.rarityId());
      return rarity == null ? 0L : rarity.defaultPrice(cosmetic.type());
   }

   public void setListed(String type, String id, boolean listed) {
      ItemShopTypes.Cosmetic old = this.cosmetic(type, id);
      if (old == null) {
         return;
      }
      ItemShopTypes.Cosmetic updated = new ItemShopTypes.Cosmetic(
         old.type(), old.id(), old.displayName(), old.lore(), old.rarityId(), old.price(), listed, listed,
         old.featured(), old.permission(), old.servers(), old.material(), old.itemModel(), old.headTexture(),
         old.offsetX(), old.offsetY(), old.offsetZ(), old.scale(), old.rotX(), old.rotY(), old.rotZ(), old.tagText(), old.position()
      );
      if ("tag".equals(type)) {
         this.tags.put(id.toLowerCase(Locale.ROOT), updated);
      } else {
         this.hats.put(id.toLowerCase(Locale.ROOT), updated);
      }
   }

   public void setPrice(String type, String id, long price) {
      ItemShopTypes.Cosmetic old = this.cosmetic(type, id);
      if (old == null) {
         return;
      }
      ItemShopTypes.Cosmetic updated = new ItemShopTypes.Cosmetic(
         old.type(), old.id(), old.displayName(), old.lore(), old.rarityId(), price, old.enabled(), old.inRotation(),
         old.featured(), old.permission(), old.servers(), old.material(), old.itemModel(), old.headTexture(),
         old.offsetX(), old.offsetY(), old.offsetZ(), old.scale(), old.rotX(), old.rotY(), old.rotZ(), old.tagText(), old.position()
      );
      if ("tag".equals(type)) {
         this.tags.put(id.toLowerCase(Locale.ROOT), updated);
      } else {
         this.hats.put(id.toLowerCase(Locale.ROOT), updated);
      }
   }
}

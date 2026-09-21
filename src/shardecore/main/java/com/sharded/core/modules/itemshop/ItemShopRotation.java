package com.sharded.core.modules.itemshop;

import com.sharded.core.modules.wardrobe.HatCatalog;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ItemShopRotation {
   private final ItemShopCatalog catalog;
   private final ItemShopDatabase database;
   private final Random random = new Random();
   private final List<ItemShopTypes.Listing> listings = new CopyOnWriteArrayList<>();
   private volatile long expiresAt;
   private volatile String serverKey = "global";

   public ItemShopRotation(ItemShopCatalog catalog, ItemShopDatabase database) {
      this.catalog = catalog;
      this.database = database;
   }

   public List<ItemShopTypes.Listing> listings() {
      return List.copyOf(this.listings);
   }

   public long expiresAt() {
      return this.expiresAt;
   }

   public String serverKey() {
      return this.serverKey;
   }

   public boolean contains(String type, String id) {
      return this.listings.stream().anyMatch(l -> l.type().equals(type) && l.id().equalsIgnoreCase(id));
   }

   public synchronized void loadOrRoll(YamlConfiguration shop, boolean force) {
      boolean shared = shop.getBoolean("share-rotation-across-servers", false);
      this.serverKey = shared ? "global" : shop.getString("server-id", "CrystalPVP");
      long storedExpiry = this.database.loadExpiry(this.serverKey);
      List<ItemShopTypes.Listing> stored = this.database.loadRotation(this.serverKey);
      long now = System.currentTimeMillis();
      if (!force && storedExpiry > now && !stored.isEmpty() && this.valid(stored)) {
         this.listings.clear();
         this.listings.addAll(stored);
         this.expiresAt = storedExpiry;
         return;
      }
      this.roll(shop);
   }

   private boolean valid(List<ItemShopTypes.Listing> stored) {
      for (ItemShopTypes.Listing listing : stored) {
         ItemShopTypes.Cosmetic cosmetic = this.catalog.cosmetic(listing.type(), listing.id());
         if (cosmetic == null) {
            return false;
         }
         if ("hat".equals(listing.type()) && HatCatalog.isRemoved(cosmetic.id(), cosmetic.material())) {
            return false;
         }
      }
      return true;
   }

   public synchronized void roll(YamlConfiguration shop) {
      int hatCount = shop.getInt("rotation.hat-slots", 4);
      int tagCount = shop.getInt("rotation.tag-slots", 4);
      int window = shop.getInt("rotation.no-repeat-window", 1);
      int minEpic = shop.getInt("rotation.guaranteed-minimums.epic-or-better", 0);
      Set<String> banned = this.database.recentIds(this.serverKey, window);
      String serverId = shop.getString("server-id", "CrystalPVP");
      List<ItemShopTypes.Listing> next = new ArrayList<>();
      next.addAll(this.pick("hat", hatCount, banned, serverId, minEpic));
      next.addAll(this.pick("tag", tagCount, banned, serverId, 0));
      this.expiresAt = this.nextExpiry(shop);
      this.listings.clear();
      this.listings.addAll(next);
      this.database.saveRotation(this.serverKey, next, this.expiresAt);
   }

   private List<ItemShopTypes.Listing> pick(String type, int count, Set<String> banned, String serverId, int minEpicOrBetter) {
      List<ItemShopTypes.Cosmetic> pool = new ArrayList<>();
      List<ItemShopTypes.Cosmetic> featured = new ArrayList<>();
      Iterable<ItemShopTypes.Cosmetic> source = "tag".equals(type) ? this.catalog.tags() : this.catalog.hats();
      for (ItemShopTypes.Cosmetic cosmetic : source) {
         if (!cosmetic.enabled() || !cosmetic.allowedOn(serverId)) {
            continue;
         }
         if ("hat".equals(type) && HatCatalog.isRemoved(cosmetic.id(), cosmetic.material())) {
            continue;
         }
         if (cosmetic.featured()) {
            featured.add(cosmetic);
         } else if (cosmetic.inRotation() && !banned.contains(cosmetic.id())) {
            pool.add(cosmetic);
         }
      }
      featured.sort(Comparator.comparingInt(c -> {
         ItemShopTypes.Rarity rarity = this.catalog.rarity(c.rarityId());
         return rarity == null ? 0 : -rarity.sortOrder();
      }));
      List<ItemShopTypes.Cosmetic> chosen = new ArrayList<>();
      for (ItemShopTypes.Cosmetic cosmetic : featured) {
         if (chosen.size() >= count) {
            break;
         }
         chosen.add(cosmetic);
      }
      while (chosen.size() < count && !pool.isEmpty()) {
         ItemShopTypes.Cosmetic pick = this.weighted(pool);
         if (pick == null) {
            break;
         }
         pool.remove(pick);
         chosen.add(pick);
      }
      this.ensureEpic(chosen, pool, minEpicOrBetter);
      List<ItemShopTypes.Listing> listings = new ArrayList<>();
      for (int i = 0; i < chosen.size(); i++) {
         listings.add(new ItemShopTypes.Listing(type, chosen.get(i).id(), i));
      }
      return listings;
   }

   private void ensureEpic(List<ItemShopTypes.Cosmetic> chosen, List<ItemShopTypes.Cosmetic> pool, int min) {
      if (min <= 0) {
         return;
      }
      long have = chosen.stream().filter(this::epicOrBetter).count();
      if (have >= min) {
         return;
      }
      List<ItemShopTypes.Cosmetic> upgrades = new ArrayList<>();
      for (ItemShopTypes.Cosmetic cosmetic : pool) {
         if (this.epicOrBetter(cosmetic)) {
            upgrades.add(cosmetic);
         }
      }
      for (int i = 0; i < chosen.size() && have < min && !upgrades.isEmpty(); i++) {
         if (!this.epicOrBetter(chosen.get(i)) && !chosen.get(i).featured()) {
            ItemShopTypes.Cosmetic upgrade = upgrades.remove(0);
            pool.remove(upgrade);
            chosen.set(i, upgrade);
            have++;
         }
      }
   }

   private boolean epicOrBetter(ItemShopTypes.Cosmetic cosmetic) {
      ItemShopTypes.Rarity rarity = this.catalog.rarity(cosmetic.rarityId());
      if (rarity == null) {
         return false;
      }
      String id = rarity.id().toLowerCase(Locale.ROOT);
      return id.equals("epic") || id.equals("legendary") || id.equals("sharded") || id.equals("mythic");
   }

   private ItemShopTypes.Cosmetic weighted(List<ItemShopTypes.Cosmetic> pool) {
      int total = 0;
      for (ItemShopTypes.Cosmetic cosmetic : pool) {
         ItemShopTypes.Rarity rarity = this.catalog.rarity(cosmetic.rarityId());
         total += rarity == null ? 1 : Math.max(0, rarity.weight());
      }
      if (total <= 0) {
         return pool.isEmpty() ? null : pool.get(this.random.nextInt(pool.size()));
      }
      int roll = this.random.nextInt(total);
      int cursor = 0;
      for (ItemShopTypes.Cosmetic cosmetic : pool) {
         ItemShopTypes.Rarity rarity = this.catalog.rarity(cosmetic.rarityId());
         cursor += rarity == null ? 1 : Math.max(0, rarity.weight());
         if (roll < cursor) {
            return cosmetic;
         }
      }
      return pool.get(pool.size() - 1);
   }

   private long nextExpiry(YamlConfiguration shop) {
      ZoneId zone;
      try {
         zone = ZoneId.of(shop.getString("rotation.timezone", "Europe/London"));
      } catch (Exception ex) {
         zone = ZoneId.of("Europe/London");
      }
      int hours = Math.max(1, shop.getInt("rotation.interval-hours", 24));
      String timeRaw = shop.getString("rotation.refresh-time", "00:00");
      LocalTime time;
      try {
         time = LocalTime.parse(timeRaw);
      } catch (Exception ex) {
         time = LocalTime.MIDNIGHT;
      }
      ZonedDateTime now = ZonedDateTime.now(zone);
      ZonedDateTime next = now.with(time);
      if (!next.isAfter(now)) {
         next = next.plusHours(hours);
      }
      while (next.isBefore(now.plusMinutes(1))) {
         next = next.plusHours(hours);
      }
      return next.toInstant().toEpochMilli();
   }
}

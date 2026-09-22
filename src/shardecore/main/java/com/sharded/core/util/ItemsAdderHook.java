package com.sharded.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class ItemsAdderHook {
   private static final Map<String, List<String>> HAT_ALIASES = Map.ofEntries(
      Map.entry("tophat", List.of("tophat", "hats:tophat")),
      Map.entry("top_hat", List.of("top_hat", "somehats:top_hat", "somehats:tophat")),
      Map.entry("crown", List.of("crown", "gold_crown", "hats:crown", "somehats:crown")),
      Map.entry(
         "propeller_hat",
         List.of("propeller_hat", "propellerhat", "propeller", "hats:propeller_hat", "hats:propellerhat", "hats:propeller")
      ),
      Map.entry("clown_mask", List.of("clown_mask", "clownmask", "clown", "hats:clown_mask", "hats:clownmask", "hats:clown")),
      Map.entry(
         "sstraw_hat",
         List.of("sstraw_hat", "straw_hat", "strawhat", "straw", "hats:straw_hat", "hats:sstraw_hat", "hats:strawhat")
      ),
      Map.entry("straw_hat", List.of("straw_hat", "sstraw_hat", "hats:straw_hat", "hats:sstraw_hat")),
      Map.entry("dimmadome", List.of("dimmadome", "dimma_dome", "hats:dimmadome", "hats:dimma_dome", "hats:doug_dimmadome")),
      Map.entry("pharaon_hat", List.of("pharaon_hat", "pharaoh_hat", "somehats:pharaon_hat", "somehats:pharaoh_hat")),
      Map.entry("pharaoh_hat", List.of("pharaoh_hat", "pharaon_hat", "somehats:pharaoh_hat", "somehats:pharaon_hat"))
   );

   private ItemsAdderHook() {
   }

   public static boolean isAvailable() {
      try {
         Class.forName("dev.lone.itemsadder.api.CustomStack");
         return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
      } catch (Throwable throwable) {
         return false;
      }
   }

   public static ItemStack getItem(String id) {
      if (id != null && !id.isBlank() && isAvailable()) {
         try {
            Class<?> oclass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object object = oclass.getMethod("getInstance", String.class).invoke(null, id);
            if (object == null) {
               return null;
            } else {
               ItemStack itemstack = (ItemStack)oclass.getMethod("getItemStack").invoke(object);
               return itemstack != null && !itemstack.getType().isAir() ? itemstack.clone() : null;
            }
         } catch (Throwable throwable) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static ItemStack resolve(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim();
         if (s.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            return parseVanillaMaterial(s.substring("minecraft:".length()));
         } else {
            if (!s.contains(":")) {
               ItemStack itemstack = parseVanillaMaterial(s);
               if (itemstack != null) {
                  return itemstack;
               }
            }

            ItemStack itemstack1 = resolveCustom(s);
            return itemstack1 != null ? itemstack1 : parseVanillaMaterial(s);
         }
      } else {
         return null;
      }
   }

   public static ItemStack parseItem(String raw) {
      ItemStack itemstack = resolve(raw);
      return itemstack != null ? itemstack : parseVanillaMaterial(raw);
   }

   public static ItemStack resolveCustom(String id) {
      if (id != null && !id.isBlank() && isAvailable()) {
         for (String s : candidateIds(id)) {
            ItemStack itemstack = getItem(s);
            if (itemstack != null) {
               return itemstack;
            }
         }

         return matchRegistry(id);
      } else {
         return null;
      }
   }

   public static List<String> listRegistryIds() {
      if (!isAvailable()) {
         return List.of();
      } else {
         try {
            Class<?> oclass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            if (oclass.getMethod("getNamespacedIdsInRegistry").invoke(null) instanceof Collection<?> collection && !collection.isEmpty()) {
               ArrayList<String> arraylist = new ArrayList<>();

               for (Object object : collection) {
                  if (object != null) {
                     arraylist.add(object.toString());
                  }
               }

               return List.copyOf(arraylist);
            }

            return List.of();
         } catch (Throwable throwable) {
            return List.of();
         }
      }
   }

   public static boolean isRegistryReady() {
      return !isAvailable() || !listRegistryIds().isEmpty();
   }

   public static ItemStack matchRegistry(String... needles) {
      List<String> list = listRegistryIds();
      if (list.isEmpty()) {
         return null;
      } else {
         LinkedHashSet<String> linkedhashset = new LinkedHashSet<>();

         for (String s : needles) {
            if (s != null && !s.isBlank()) {
               linkedhashset.add(normalize(s));
               String s1 = localPart(s);
               linkedhashset.add(normalize(s1));

               for (String s2 : hatAliases(s1)) {
                  linkedhashset.add(normalize(s2));
               }
            }
         }

         linkedhashset.removeIf(String::isBlank);

         for (String s3 : list) {
            String s5 = normalize(s3);
            String s7 = normalize(localPart(s3));
            if (linkedhashset.contains(s5) || linkedhashset.contains(s7)) {
               ItemStack itemstack = getItem(s3);
               if (itemstack != null) {
                  return itemstack;
               }
            }
         }

         for (String s4 : list) {
            String s6 = normalize(localPart(s4));

            for (String s8 : linkedhashset) {
               if (!s8.isBlank() && (s6.contains(s8) || s8.contains(s6))) {
                  ItemStack itemstack1 = getItem(s4);
                  if (itemstack1 != null) {
                     return itemstack1;
                  }
               }
            }
         }

         return null;
      }
   }

   public static List<String> hatAliases(String hatId) {
      if (hatId != null && !hatId.isBlank()) {
         String s = localPart(hatId).toLowerCase(Locale.ROOT).replace("-", "_");
         List<String> list = HAT_ALIASES.get(s);
         if (list == null && s.equals("top_hat")) {
            list = HAT_ALIASES.get("top_hat");
         }

         if (list == null && (s.equals("straw_hat") || s.equals("strawhat"))) {
            list = HAT_ALIASES.get("sstraw_hat");
         }

         return list == null ? List.of() : list;
      } else {
         return List.of();
      }
   }

   private static ItemStack parseVanillaMaterial(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim();
         if (s.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            s = s.substring("minecraft:".length());
         } else if (s.contains(":")) {
            return null;
         }

         Material material = Material.matchMaterial(s.toUpperCase(Locale.ROOT));
         return material != null && material.isItem() && !material.isAir() ? new ItemStack(material) : null;
      } else {
         return null;
      }
   }

   static List<String> candidateIds(String raw) {
      LinkedHashSet<String> linkedhashset = new LinkedHashSet<>();
      addVariants(linkedhashset, raw);
      if (raw.startsWith("itemsadder-")) {
         addVariants(linkedhashset, raw.substring("itemsadder-".length()));
      }

      String s = localPart(raw);
      addVariants(linkedhashset, s);

      for (String s1 : hatAliases(s)) {
         addVariants(linkedhashset, s1);
         if (!s1.contains(":")) {
            addVariants(linkedhashset, "hats:" + s1);
            addVariants(linkedhashset, "HATS:" + s1);
         }
      }

      if (raw.contains(":")) {
         String s2 = raw.substring(0, raw.indexOf(58));
         String s3 = raw.substring(raw.indexOf(58) + 1);
         if (s2.startsWith("itemsadder-")) {
            addVariants(linkedhashset, s2.substring("itemsadder-".length()) + ":" + s3);
         }

         addVariants(linkedhashset, s2 + ":" + s3);
         addLocalSpellings(linkedhashset, s2, s3);
      } else if (!hatAliases(raw).isEmpty()) {
         addLocalSpellings(linkedhashset, "hats", raw);
         addLocalSpellings(linkedhashset, "HATS", raw);
      }

      return new ArrayList<>(linkedhashset);
   }

   private static void addLocalSpellings(Set<String> ids, String ns, String item) {
      LinkedHashSet<String> linkedhashset = new LinkedHashSet<>();
      linkedhashset.add(item);
      linkedhashset.add(item.replace("_", ""));
      linkedhashset.add(item.replace("-", "_"));
      linkedhashset.add(item.replace("-", ""));
      if (normalize(item).equals("tophat")) {
         linkedhashset.add("top_hat");
         linkedhashset.add("tophat");
         linkedhashset.add("TOPHAT");
         linkedhashset.add("TOP_HAT");
      }

      if (normalize(item).equals("sstrawhat") || normalize(item).equals("strawhat")) {
         linkedhashset.add("straw_hat");
         linkedhashset.add("sstraw_hat");
         linkedhashset.add("strawhat");
      }

      for (String s : linkedhashset) {
         addVariants(ids, s);
         addVariants(ids, ns + ":" + s);
      }
   }

   private static void addVariants(Set<String> ids, String id) {
      if (id != null && !id.isBlank()) {
         ids.add(id);
         ids.add(id.toLowerCase(Locale.ROOT));
         ids.add(id.toUpperCase(Locale.ROOT));
         if (id.contains(":")) {
            String s = id.substring(0, id.indexOf(58));
            String s1 = id.substring(id.indexOf(58) + 1);
            ids.add(s.toLowerCase(Locale.ROOT) + ":" + s1.toLowerCase(Locale.ROOT));
            ids.add(s.toUpperCase(Locale.ROOT) + ":" + s1.toUpperCase(Locale.ROOT));
            ids.add(s.toUpperCase(Locale.ROOT) + ":" + s1.toLowerCase(Locale.ROOT));
            ids.add(s.toLowerCase(Locale.ROOT) + ":" + s1.toUpperCase(Locale.ROOT));
            String s2 = s1.contains("_") ? s1.replace("_", "") : s1;
            if (!s2.equals(s1)) {
               ids.add(s.toLowerCase(Locale.ROOT) + ":" + s2.toLowerCase(Locale.ROOT));
               ids.add(s.toUpperCase(Locale.ROOT) + ":" + s2.toUpperCase(Locale.ROOT));
            }
         }
      }
   }

   static String normalize(String value) {
      return value == null ? "" : value.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
   }

   private static String localPart(String id) {
      if (id == null) {
         return "";
      } else {
         int i = id.lastIndexOf(58);
         return i >= 0 ? id.substring(i + 1) : id;
      }
   }
}

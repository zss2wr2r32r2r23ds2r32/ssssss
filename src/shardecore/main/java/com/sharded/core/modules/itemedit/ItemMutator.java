package com.sharded.core.modules.itemedit;

import com.sharded.core.util.BundleColorUtil;
import com.sharded.core.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Axolotl.Variant;
import org.bukkit.entity.TropicalFish.Pattern;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.bukkit.inventory.meta.BookMeta.Generation;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

final class ItemMutator {
   private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

   private ItemMutator() {
   }

   static ItemMutator.Outcome name(ItemStack stack, String text) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         itemmeta.displayName(Text.c(text));
         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome loreAdd(ItemStack stack, String text) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         List<Component> list = loreOf(itemmeta);
         list.add(Text.c(text));
         itemmeta.lore(list);
         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome loreSet(ItemStack stack, int index1, String text) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         List<Component> list = loreOf(itemmeta);
         int i = index1 - 1;
         if (i >= 0 && i < list.size()) {
            list.set(i, Text.c(text));
            itemmeta.lore(list);
            stack.setItemMeta(itemmeta);
            return ItemMutator.Outcome.success();
         } else {
            return ItemMutator.Outcome.fail("invalid", "%value%", String.valueOf(index1));
         }
      }
   }

   static ItemMutator.Outcome loreReplaceAll(ItemStack stack, String text) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         List<Component> list = new ArrayList<>();

         for (String s : text.split("\\\\n|\\n")) {
            list.add(Text.c(s));
         }

         itemmeta.lore(list);
         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome loreInsert(ItemStack stack, int index1, String text) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         List<Component> list = loreOf(itemmeta);
         int i = Math.max(0, Math.min(list.size(), index1 - 1));
         list.add(i, Text.c(text));
         itemmeta.lore(list);
         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome loreRemove(ItemStack stack, int index1) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         List<Component> list = loreOf(itemmeta);
         int i = index1 - 1;
         if (i >= 0 && i < list.size()) {
            list.remove(i);
            itemmeta.lore(list.isEmpty() ? null : list);
            stack.setItemMeta(itemmeta);
            return ItemMutator.Outcome.success();
         } else {
            return ItemMutator.Outcome.fail("invalid", "%value%", String.valueOf(index1));
         }
      }
   }

   static ItemMutator.Outcome loreClear(ItemStack stack) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         itemmeta.lore(null);
         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static List<String> loreCopy(ItemStack stack) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return List.of();
      } else {
         List<String> list = new ArrayList<>();

         for (Component component : loreOf(itemmeta)) {
            list.add(LEGACY.serialize(component));
         }

         return list;
      }
   }

   static ItemMutator.Outcome lorePaste(ItemStack stack, List<String> lines) {
      if (lines != null && !lines.isEmpty()) {
         ItemMeta itemmeta = stack.getItemMeta();
         if (itemmeta == null) {
            return ItemMutator.Outcome.fail("need-item");
         } else {
            List<Component> list = new ArrayList<>();

            for (String s : lines) {
               list.add(Text.c(s));
            }

            itemmeta.lore(list);
            stack.setItemMeta(itemmeta);
            return ItemMutator.Outcome.success();
         }
      } else {
         return ItemMutator.Outcome.fail("clipboard-empty");
      }
   }

   static ItemMutator.Outcome unbreakable(ItemStack stack, Boolean value) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         boolean flag = value == null ? !itemmeta.isUnbreakable() : value;
         itemmeta.setUnbreakable(flag);
         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome glow(ItemStack stack, Boolean value) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         boolean flag = value == null ? !Boolean.TRUE.equals(itemmeta.getEnchantmentGlintOverride()) : value;
         if (flag) {
            itemmeta.setEnchantmentGlintOverride(true);
            itemmeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
         } else {
            itemmeta.setEnchantmentGlintOverride(null);
            itemmeta.removeItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
         }

         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome hide(ItemStack stack, String kind, Boolean value) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         boolean flag = value == null ? true : value;
         String s = kind.toLowerCase(Locale.ROOT);
         if (s.equals("tooltip")) {
            itemmeta.setHideTooltip(flag);
         } else if (!s.equals("flags") && !s.equals("all")) {
            if (s.equals("enchants") || s.equals("enchantments")) {
               flag(itemmeta, ItemFlag.HIDE_ENCHANTS, flag);
            } else if (s.equals("potion") || s.equals("effects") || s.equals("additional")) {
               flag(itemmeta, ItemFlag.HIDE_ADDITIONAL_TOOLTIP, flag);
            } else if (s.equals("attributes")) {
               flag(itemmeta, ItemFlag.HIDE_ATTRIBUTES, flag);
            } else if (s.equals("unbreakable")) {
               flag(itemmeta, ItemFlag.HIDE_UNBREAKABLE, flag);
            } else if (s.equals("dye")) {
               flag(itemmeta, ItemFlag.HIDE_DYE, flag);
            } else {
               if (!s.equals("trim")) {
                  return ItemMutator.Outcome.fail("invalid", "%value%", kind);
               }

               flag(itemmeta, ItemFlag.HIDE_ARMOR_TRIM, flag);
            }
         } else if (flag) {
            itemmeta.addItemFlags(ItemFlag.values());
         } else {
            itemmeta.removeItemFlags(ItemFlag.values());
         }

         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome tooltipStyle(ItemStack stack, String raw) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         if (!raw.equalsIgnoreCase("clear") && !raw.equalsIgnoreCase("reset")) {
            NamespacedKey namespacedkey = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
            if (namespacedkey == null) {
               return ItemMutator.Outcome.fail("invalid", "%value%", raw);
            }

            itemmeta.setTooltipStyle(namespacedkey);
         } else {
            itemmeta.setTooltipStyle(null);
         }

         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome color(ItemStack stack, String raw) {
      Color color = parseColor(raw);
      if (color == null) {
         return ItemMutator.Outcome.fail("invalid", "%value%", raw);
      } else {
         ItemMeta itemmeta = stack.getItemMeta();
         if (itemmeta instanceof PotionMeta potionmeta) {
            potionmeta.setColor(color);
            stack.setItemMeta(potionmeta);
            return ItemMutator.Outcome.success();
         } else if (itemmeta instanceof LeatherArmorMeta leatherarmormeta) {
            leatherarmormeta.setColor(color);
            stack.setItemMeta(leatherarmormeta);
            return ItemMutator.Outcome.success();
         } else {
            try {
               stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
               return ItemMutator.Outcome.success();
            } catch (RuntimeException runtimeexception) {
               return ItemMutator.Outcome.fail("not-colorable");
            }
         }
      }
   }

   static ItemMutator.Outcome potionAdd(ItemStack stack, String effectName, int seconds, int amplifier) {
      if (stack.getItemMeta() instanceof PotionMeta potionmeta) {
         PotionEffectType potioneffecttype = effect(effectName);
         if (potioneffecttype == null) {
            return ItemMutator.Outcome.fail("invalid", "%value%", effectName);
         } else {
            int i = Math.max(1, seconds) * 20;
            potionmeta.addCustomEffect(new PotionEffect(potioneffecttype, i, Math.max(0, amplifier)), true);
            stack.setItemMeta(potionmeta);
            return ItemMutator.Outcome.success();
         }
      } else {
         return ItemMutator.Outcome.fail("not-potion");
      }
   }

   static ItemMutator.Outcome potionClear(ItemStack stack) {
      if (stack.getItemMeta() instanceof PotionMeta potionmeta) {
         potionmeta.clearCustomEffects();
         stack.setItemMeta(potionmeta);
         return ItemMutator.Outcome.success();
      } else {
         return ItemMutator.Outcome.fail("not-potion");
      }
   }

   static ItemMutator.Outcome enchant(ItemStack stack, String name, int level) {
      Enchantment enchantment = enchantment(name);
      if (enchantment == null) {
         return ItemMutator.Outcome.fail("invalid", "%value%", name);
      } else {
         ItemMeta itemmeta = stack.getItemMeta();
         if (itemmeta instanceof EnchantmentStorageMeta enchantmentstoragemeta) {
            enchantmentstoragemeta.addStoredEnchant(enchantment, Math.max(1, level), true);
            stack.setItemMeta(enchantmentstoragemeta);
            return ItemMutator.Outcome.success();
         } else if (itemmeta == null) {
            return ItemMutator.Outcome.fail("need-item");
         } else {
            itemmeta.addEnchant(enchantment, Math.max(1, level), true);
            stack.setItemMeta(itemmeta);
            return ItemMutator.Outcome.success();
         }
      }
   }

   static ItemMutator.Outcome unenchant(ItemStack stack, String name) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else if (name != null && !name.isBlank() && !name.equalsIgnoreCase("all")) {
         Enchantment enchantment1 = enchantment(name);
         if (enchantment1 == null) {
            return ItemMutator.Outcome.fail("invalid", "%value%", name);
         } else if (itemmeta instanceof EnchantmentStorageMeta enchantmentstoragemeta1) {
            enchantmentstoragemeta1.removeStoredEnchant(enchantment1);
            stack.setItemMeta(enchantmentstoragemeta1);
            return ItemMutator.Outcome.success();
         } else {
            itemmeta.removeEnchant(enchantment1);
            stack.setItemMeta(itemmeta);
            return ItemMutator.Outcome.success();
         }
      } else if (!(itemmeta instanceof EnchantmentStorageMeta enchantmentstoragemeta)) {
         itemmeta.removeEnchantments();
         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      } else {
         for (Enchantment enchantment : List.copyOf(enchantmentstoragemeta.getStoredEnchants().keySet())) {
            enchantmentstoragemeta.removeStoredEnchant(enchantment);
         }

         stack.setItemMeta(enchantmentstoragemeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome attribute(ItemStack stack, String attrName, double amount, String opName) {
      Attribute attribute = attribute(attrName);
      if (attribute == null) {
         return ItemMutator.Outcome.fail("invalid", "%value%", attrName);
      } else {
         ItemMeta itemmeta = stack.getItemMeta();
         if (itemmeta == null) {
            return ItemMutator.Outcome.fail("need-item");
         } else {
            Operation operation = operation(opName);
            NamespacedKey namespacedkey = new NamespacedKey("itemedit", attribute.getKey().getKey());
            itemmeta.removeAttributeModifier(attribute);
            itemmeta.addAttributeModifier(attribute, new AttributeModifier(namespacedkey, amount, operation, EquipmentSlotGroup.ANY));
            stack.setItemMeta(itemmeta);
            return ItemMutator.Outcome.success();
         }
      }
   }

   static ItemMutator.Outcome repair(ItemStack stack) {
      if (stack.getItemMeta() instanceof Damageable damageable) {
         damageable.setDamage(0);
         stack.setItemMeta(damageable);
         return ItemMutator.Outcome.success();
      } else {
         return ItemMutator.Outcome.fail("not-damageable");
      }
   }

   static ItemMutator.Outcome durability(ItemStack stack, int damage) {
      if (stack.getItemMeta() instanceof Damageable damageable) {
         damageable.setDamage(Math.max(0, damage));
         stack.setItemMeta(damageable);
         return ItemMutator.Outcome.success();
      } else {
         return ItemMutator.Outcome.fail("not-damageable");
      }
   }

   static ItemMutator.Outcome maxDurability(ItemStack stack, int max) {
      if (stack.getItemMeta() instanceof Damageable damageable) {
         damageable.setMaxDamage(Math.max(1, max));
         stack.setItemMeta(damageable);
         return ItemMutator.Outcome.success();
      } else {
         return ItemMutator.Outcome.fail("not-damageable");
      }
   }

   static ItemMutator.Outcome customModelData(ItemStack stack, int data) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         if (data <= 0) {
            itemmeta.setCustomModelData(null);
         } else {
            itemmeta.setCustomModelData(data);
         }

         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome itemModel(ItemStack stack, String raw) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         if (!raw.equalsIgnoreCase("clear") && !raw.equalsIgnoreCase("reset")) {
            NamespacedKey namespacedkey = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
            if (namespacedkey == null) {
               return ItemMutator.Outcome.fail("invalid", "%value%", raw);
            }

            itemmeta.setItemModel(namespacedkey);
         } else {
            itemmeta.setItemModel(null);
         }

         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome rarity(ItemStack stack, String raw) {
      ItemMeta itemmeta = stack.getItemMeta();
      if (itemmeta == null) {
         return ItemMutator.Outcome.fail("need-item");
      } else {
         try {
            itemmeta.setRarity(ItemRarity.valueOf(raw.toUpperCase(Locale.ROOT)));
         } catch (IllegalArgumentException illegalargumentexception) {
            return ItemMutator.Outcome.fail("invalid", "%value%", raw);
         }

         stack.setItemMeta(itemmeta);
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome food(ItemStack stack, int nutrition, float saturation, boolean always, boolean drink) {
      stack.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(Math.max(0, nutrition)).saturation(saturation).canAlwaysEat(always));
      stack.setData(
         DataComponentTypes.CONSUMABLE,
         Consumable.consumable()
            .animation(drink ? ItemUseAnimation.DRINK : ItemUseAnimation.EAT)
            .consumeSeconds(drink ? 1.6F : 1.6F)
            .hasConsumeParticles(!drink)
      );
      return ItemMutator.Outcome.success();
   }

   static ItemMutator.Outcome wear(ItemStack stack, String slotName) {
      EquipmentSlot equipmentslot = slot(slotName);
      if (equipmentslot == null) {
         return ItemMutator.Outcome.fail("invalid", "%value%", slotName);
      } else {
         stack.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(equipmentslot));
         return ItemMutator.Outcome.success();
      }
   }

   static ItemMutator.Outcome compass(ItemStack stack, double x, double y, double z, String worldName) {
      if (!(stack.getItemMeta() instanceof CompassMeta compassmeta)) {
         return ItemMutator.Outcome.fail("not-compass");
      } else {
         World world = worldName != null && !worldName.isBlank()
            ? Bukkit.getWorld(worldName)
            : (Bukkit.getWorlds().isEmpty() ? null : (World)Bukkit.getWorlds().get(0));
         if (world == null) {
            return ItemMutator.Outcome.fail("invalid", "%value%", worldName == null ? "world" : worldName);
         } else {
            compassmeta.setLodestone(new Location(world, x, y, z));
            compassmeta.setLodestoneTracked(false);
            stack.setItemMeta(compassmeta);
            return ItemMutator.Outcome.success();
         }
      }
   }

   static ItemMutator.Outcome axolotl(ItemStack stack, String variantName) {
      if (stack.getItemMeta() instanceof AxolotlBucketMeta axolotlbucketmeta) {
         try {
            axolotlbucketmeta.setVariant(Variant.valueOf(variantName.toUpperCase(Locale.ROOT)));
         } catch (IllegalArgumentException illegalargumentexception) {
            return ItemMutator.Outcome.fail("invalid", "%value%", variantName);
         }

         stack.setItemMeta(axolotlbucketmeta);
         return ItemMutator.Outcome.success();
      } else {
         return ItemMutator.Outcome.fail("not-axolotl");
      }
   }

   static ItemMutator.Outcome tropic(ItemStack stack, String patternName, String bodyName, String patternColorName) {
      if (stack.getItemMeta() instanceof TropicalFishBucketMeta tropicalfishbucketmeta) {
         try {
            tropicalfishbucketmeta.setPattern(Pattern.valueOf(patternName.toUpperCase(Locale.ROOT)));
            tropicalfishbucketmeta.setBodyColor(DyeColor.valueOf(bodyName.toUpperCase(Locale.ROOT)));
            tropicalfishbucketmeta.setPatternColor(DyeColor.valueOf(patternColorName.toUpperCase(Locale.ROOT)));
         } catch (IllegalArgumentException illegalargumentexception) {
            return ItemMutator.Outcome.fail("invalid", "%value%", patternName + " " + bodyName + " " + patternColorName);
         }

         stack.setItemMeta(tropicalfishbucketmeta);
         return ItemMutator.Outcome.success();
      } else {
         return ItemMutator.Outcome.fail("not-tropic");
      }
   }

   static ItemMutator.Outcome trim(ItemStack stack, String patternName, String materialName) {
      if (stack.getItemMeta() instanceof ArmorMeta armormeta) {
         TrimPattern trimpattern = keyed(Registry.TRIM_PATTERN, patternName);
         TrimMaterial material = keyed(Registry.TRIM_MATERIAL, materialName);
         if (trimpattern == null) {
            return ItemMutator.Outcome.fail("invalid", "%value%", patternName);
         } else if (material == null) {
            return ItemMutator.Outcome.fail("invalid", "%value%", materialName);
         } else {
            armormeta.setTrim(new ArmorTrim(material, trimpattern));
            stack.setItemMeta(armormeta);
            return ItemMutator.Outcome.success();
         }
      } else {
         return ItemMutator.Outcome.fail("not-armor");
      }
   }

   static ItemMutator.Outcome book(ItemStack stack, String generationName) {
      if (stack.getItemMeta() instanceof BookMeta bookmeta) {
         String s = generationName.toLowerCase(Locale.ROOT).replace('-', '_');

         Generation generation = switch (s) {
            case "original", "orig" -> Generation.ORIGINAL;
            case "copy", "copy_of_original", "copyoforiginal" -> Generation.COPY_OF_ORIGINAL;
            case "copy_of_copy", "copyofcopy" -> Generation.COPY_OF_COPY;
            case "tattered", "tatter" -> Generation.TATTERED;
            default -> null;
         };
         if (generation == null) {
            return ItemMutator.Outcome.fail("invalid", "%value%", generationName);
         } else {
            bookmeta.setGeneration(generation);
            stack.setItemMeta(bookmeta);
            return ItemMutator.Outcome.success();
         }
      } else {
         return ItemMutator.Outcome.fail("not-book");
      }
   }

   static Color parseColor(String raw) {
      if (raw != null && !raw.isBlank()) {
         Color color = BundleColorUtil.parse(raw);
         if (color != null) {
            return color;
         } else {
            String s = raw.replace(" ", "");
            String[] astring = s.split(",");
            if (astring.length == 3) {
               try {
                  return Color.fromRGB(clamp(Integer.parseInt(astring[0])), clamp(Integer.parseInt(astring[1])), clamp(Integer.parseInt(astring[2])));
               } catch (NumberFormatException numberformatexception) {
                  return null;
               }
            } else {
               try {
                  return DyeColor.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_')).getColor();
               } catch (IllegalArgumentException illegalargumentexception) {
                  return null;
               }
            }
         }
      } else {
         return null;
      }
   }

   static Enchantment enchantment(String raw) {
      return keyed(Registry.ENCHANTMENT, raw);
   }

   static PotionEffectType effect(String raw) {
      PotionEffectType potioneffecttype = keyed(Registry.POTION_EFFECT_TYPE, raw);
      return potioneffecttype != null ? potioneffecttype : PotionEffectType.getByName(raw.toUpperCase(Locale.ROOT).replace(' ', '_'));
   }

   static Attribute attribute(String raw) {
      Attribute attribute = keyed(Registry.ATTRIBUTE, raw);
      if (attribute != null) {
         return attribute;
      } else {
         String s = raw.toUpperCase(Locale.ROOT).replace(' ', '_').replace('.', '_');
         if (s.startsWith("GENERIC_")) {
            s = s.substring("GENERIC_".length());
         }

         try {
            return Attribute.valueOf(s);
         } catch (IllegalArgumentException illegalargumentexception) {
            return null;
         }
      }
   }

   static PatternType patternType(String raw) {
      return keyed(Registry.BANNER_PATTERN, raw);
   }

   static List<Component> loreOf(ItemMeta meta) {
      List<Component> list = meta.lore();
      return list == null ? new ArrayList<>() : new ArrayList<>(list);
   }

   static Boolean bool(String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.toLowerCase(Locale.ROOT);

         return switch (s) {
            case "true", "on", "yes", "enable", "enabled" -> true;
            case "false", "off", "no", "disable", "disabled" -> false;
            default -> null;
         };
      } else {
         return null;
      }
   }

   private static void flag(ItemMeta meta, ItemFlag flag, boolean add) {
      if (add) {
         meta.addItemFlags(new ItemFlag[]{flag});
      } else {
         meta.removeItemFlags(new ItemFlag[]{flag});
      }
   }

   private static Operation operation(String raw) {
      if (raw == null) {
         return Operation.ADD_NUMBER;
      } else {
         String s = raw.toLowerCase(Locale.ROOT).replace('-', '_');

         return switch (s) {
            case "multiply", "scalar", "add_scalar" -> Operation.ADD_SCALAR;
            case "multiply_base", "multiply_scalar", "multiply_scalar_1" -> Operation.MULTIPLY_SCALAR_1;
            default -> Operation.ADD_NUMBER;
         };
      }
   }

   private static EquipmentSlot slot(String raw) {
      String s = raw.toLowerCase(Locale.ROOT).replace('-', '_');

      return switch (s) {
         case "head", "helmet" -> EquipmentSlot.HEAD;
         case "chest", "chestplate", "body_armor" -> EquipmentSlot.CHEST;
         case "legs", "leggings" -> EquipmentSlot.LEGS;
         case "feet", "boots" -> EquipmentSlot.FEET;
         case "offhand", "off_hand" -> EquipmentSlot.OFF_HAND;
         case "hand", "mainhand", "main_hand" -> EquipmentSlot.HAND;
         case "body" -> EquipmentSlot.BODY;
         case "saddle" -> EquipmentSlot.SADDLE;
         default -> null;
      };
   }

   private static <T extends Keyed> T keyed(Registry<T> registry, String raw) {
      if (raw != null && !raw.isBlank()) {
         String s = raw.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
         NamespacedKey namespacedkey = s.contains(":") ? NamespacedKey.fromString(s) : NamespacedKey.minecraft(s);
         if (namespacedkey == null) {
            return null;
         } else {
            T t = (T)registry.get(namespacedkey);
            if (t != null) {
               return t;
            } else {
               String s1 = s.replace("_", "");
               return registry.stream().filter(entry -> {
                  String s2 = entry.getKey().getKey();
                  return s2.equals(s) || s2.replace("_", "").equals(s1);
               }).findFirst().orElse(null);
            }
         }
      } else {
         return null;
      }
   }

   private static int clamp(int channel) {
      return Math.max(0, Math.min(255, channel));
   }

   static record Outcome(boolean ok, String key, String... vars) {
      static ItemMutator.Outcome success() {
         return new ItemMutator.Outcome(true, "applied");
      }

      static ItemMutator.Outcome fail(String key, String... vars) {
         return new ItemMutator.Outcome(false, key, vars);
      }
   }
}

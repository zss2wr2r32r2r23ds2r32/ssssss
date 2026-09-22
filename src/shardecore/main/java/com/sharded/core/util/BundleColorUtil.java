package com.sharded.core.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DyedItemColor;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class BundleColorUtil {
   private static final Pattern HEX = Pattern.compile("(?i)(?:&#|#|0x)?([0-9a-f]{6})");
   private static final Pattern SECTION_HEX = Pattern.compile("(?i)&x(?:&([0-9a-f])){6}");

   private BundleColorUtil() {
   }

   public static Color parse(String raw) {
      if (raw != null && !raw.isBlank()) {
         Matcher matcher = SECTION_HEX.matcher(raw);
         if (matcher.find()) {
            String s = matcher.group();
            StringBuilder stringbuilder = new StringBuilder(6);

            for (int i = 0; i < s.length(); i++) {
               char c0 = s.charAt(i);
               if (Character.digit(c0, 16) >= 0 && (i == 0 || s.charAt(i - 1) == '&') && c0 != 'x' && c0 != 'X') {
                  stringbuilder.append(c0);
               }
            }

            String s1 = s.replaceAll("(?i)&x", "").replace("&", "");
            if (s1.length() >= 6) {
               return fromHex(s1.substring(0, 6));
            }
         }

         Matcher matcher1 = HEX.matcher(raw.replace(" ", ""));
         return matcher1.find() ? fromHex(matcher1.group(1)) : null;
      } else {
         return null;
      }
   }

   public static Color fromHex(String hex) {
      if (hex != null && hex.length() >= 6) {
         try {
            int i = Integer.parseInt(hex.substring(0, 6), 16);
            return Color.fromRGB(i >> 16 & 0xFF, i >> 8 & 0xFF, i & 0xFF);
         } catch (NumberFormatException numberformatexception) {
            return null;
         }
      } else {
         return null;
      }
   }

   public static String accentCode(Color color) {
      return color == null ? "&#A370EE" : String.format(Locale.ROOT, "&#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
   }

   public static Material bundleMaterial(Color color) {
      DyeColor dyecolor = nearestDye(color);
      if (dyecolor == null) {
         return Material.PURPLE_BUNDLE;
      } else {
         Material material = Material.matchMaterial(dyecolor.name() + "_BUNDLE");
         return material == null ? Material.PURPLE_BUNDLE : material;
      }
   }

   public static DyeColor nearestDye(Color color) {
      if (color == null) {
         return DyeColor.PURPLE;
      } else {
         DyeColor dyecolor = DyeColor.PURPLE;
         double d0 = Double.MAX_VALUE;

         for (DyeColor dyecolor1 : DyeColor.values()) {
            Color colorx = dyecolor1.getColor();
            double d1 = distance(color, colorx);
            if (d1 < d0) {
               d0 = d1;
               dyecolor = dyecolor1;
            }
         }

         return dyecolor;
      }
   }

   private static double distance(Color a, Color b) {
      int i = a.getRed() - b.getRed();
      int j = a.getGreen() - b.getGreen();
      int k = a.getBlue() - b.getBlue();
      return (double)(i * i + j * j + k * k);
   }

   public static ItemStack dye(ItemStack stack, Color color) {
      if (stack != null && color != null) {
         try {
            stack.setData(DataComponentTypes.DYED_COLOR, DyedItemColor.dyedItemColor(color));
         } catch (RuntimeException runtimeexception) {
         }

         return stack;
      } else {
         return stack;
      }
   }

   public static ItemStack coloredBundle(Color color) {
      Material material = bundleMaterial(color);
      ItemStack itemstack = new ItemStack(material);
      dye(itemstack, color);
      return itemstack;
   }
}

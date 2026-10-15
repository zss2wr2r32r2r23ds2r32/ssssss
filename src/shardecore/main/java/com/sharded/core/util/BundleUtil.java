package com.sharded.core.util;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import io.papermc.paper.datacomponent.item.TooltipDisplay.Builder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class BundleUtil {
   private BundleUtil() {
   }

   public static boolean stripMenuTooltip(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         boolean flag = stripBundle(item);
         return flag | stripTrimTemplate(item);
      }
   }

   public static void forceCustomTooltip(ItemStack item) {
      if (item != null) {
         Builder builder = TooltipDisplay.tooltipDisplay().addHiddenComponents(new DataComponentType[]{DataComponentTypes.ITEM_NAME});
         Material material = item.getType();
         if (material.name().endsWith("_SMITHING_TEMPLATE") || item.hasData(DataComponentTypes.PROVIDES_TRIM_MATERIAL)) {
            builder.addHiddenComponents(new DataComponentType[]{DataComponentTypes.PROVIDES_TRIM_MATERIAL});
         }

         item.setData(DataComponentTypes.TOOLTIP_DISPLAY, (TooltipDisplay)builder.build());
      }
   }

   public static boolean stripBundle(ItemStack item) {
      if (item == null || item.getType() != Material.BUNDLE) {
         return false;
      } else if (item.hasData(DataComponentTypes.TOOLTIP_DISPLAY)) {
         return false;
      } else {
         item.setData(
            DataComponentTypes.TOOLTIP_DISPLAY,
            (TooltipDisplay)TooltipDisplay.tooltipDisplay().addHiddenComponents(new DataComponentType[]{DataComponentTypes.BUNDLE_CONTENTS}).build()
         );
         return true;
      }
   }

   public static boolean stripTrimTemplate(ItemStack item) {
      if (item == null) {
         return false;
      } else {
         Material material = item.getType();
         boolean flag = material.name().endsWith("_SMITHING_TEMPLATE");
         boolean flag1 = item.hasData(DataComponentTypes.PROVIDES_TRIM_MATERIAL);
         if (!flag && !flag1) {
            return false;
         } else {
            forceCustomTooltip(item);
            return true;
         }
      }
   }
}

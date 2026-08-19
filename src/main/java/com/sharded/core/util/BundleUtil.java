package com.sharded.core.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Hides noisy item tooltips in plugin menus (bundles, smithing templates). */
public final class BundleUtil {

    private BundleUtil() {
    }

    public static boolean stripMenuTooltip(ItemStack item) {
        if (item == null) return false;
        boolean changed = stripBundle(item);
        changed |= stripTrimTemplate(item);
        return changed;
    }

    public static boolean stripBundle(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        if (item.hasData(DataComponentTypes.TOOLTIP_DISPLAY)) return false;
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.BUNDLE_CONTENTS)
                .build());
        return true;
    }

    public static boolean stripTrimTemplate(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        boolean template = type.name().endsWith("_SMITHING_TEMPLATE");
        boolean trimMaterial = item.hasData(DataComponentTypes.PROVIDES_TRIM_MATERIAL);
        if (!template && !trimMaterial) return false;
        if (item.hasData(DataComponentTypes.TOOLTIP_DISPLAY)) return false;
        var builder = TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.PROVIDES_TRIM_MATERIAL);
        if (template) {
            builder.addHiddenComponents(DataComponentTypes.ITEM_NAME);
        }
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, builder.build());
        return true;
    }
}

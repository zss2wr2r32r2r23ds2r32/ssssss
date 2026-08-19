package com.sharded.core.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Hides bundle EMPTY/FULL tooltips in menus (not when players craft). */
public final class BundleUtil {

    private BundleUtil() {
    }

    public static boolean stripMenuTooltip(ItemStack item) {
        if (item == null || item.getType() != Material.BUNDLE) return false;
        if (item.hasData(DataComponentTypes.TOOLTIP_DISPLAY)) return false;
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.BUNDLE_CONTENTS)
                .build());
        return true;
    }
}

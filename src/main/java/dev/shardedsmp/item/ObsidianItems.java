package dev.shardedsmp.item;

import dev.shardedsmp.util.ColorUtil;
import dev.shardedsmp.util.Keys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class ObsidianItems {
    private ObsidianItems() {
    }

    public static ItemStack createPiece(int pieceId) {
        ItemStack item = new ItemStack(Material.OBSIDIAN, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(ColorUtil.color("&#FF0000&lObsidian"));
        meta.lore(List.of(
                ColorUtil.color("&7A forbidden shard from the sky."),
                ColorUtil.color("&8Piece #" + pieceId)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(Keys.specialObsidian, PersistentDataType.BOOLEAN, true);
        meta.getPersistentDataContainer().set(Keys.pieceId, PersistentDataType.INTEGER, pieceId);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isSpecial(ItemStack item) {
        if (item == null || item.getType() != Material.OBSIDIAN || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(Keys.specialObsidian, PersistentDataType.BOOLEAN);
    }

    public static int pieceId(ItemStack item) {
        if (!isSpecial(item)) {
            return -1;
        }
        Integer id = item.getItemMeta().getPersistentDataContainer().get(Keys.pieceId, PersistentDataType.INTEGER);
        return id == null ? -1 : id;
    }

    public static boolean isAnyObsidian(ItemStack item) {
        return item != null && item.getType() == Material.OBSIDIAN;
    }
}

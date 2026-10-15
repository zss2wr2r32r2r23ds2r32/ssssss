package com.sharded.core.setup.util;

import com.sharded.core.ShardedCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ItemBuilder {

    private final ItemStack item;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    private ItemBuilder(ItemStack item) {
        this.item = item;
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack stack) {
        return new ItemBuilder(stack.clone());
    }

    public static ItemStack glassPane() {
        ShardedCore plugin = ShardedCore.get();
        if (plugin != null && plugin.guiNavigation() != null) {
            return plugin.guiNavigation().filler("inner");
        }
        return of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
    }

    public static ItemStack darkGlassPane() {
        ShardedCore plugin = ShardedCore.get();
        if (plugin != null && plugin.guiNavigation() != null) {
            return plugin.guiNavigation().filler("border");
        }
        return of(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
    }

    
    public static ItemBuilder dyedHorseArmor(Color color) {
        ItemBuilder builder = of(Material.LEATHER_HORSE_ARMOR);
        ItemMeta meta = builder.item.getItemMeta();
        if (meta instanceof LeatherArmorMeta leather) {
            leather.setColor(color);
            builder.item.setItemMeta(leather);
        }
        return builder;
    }

    public static ItemBuilder dyedHorseArmor(String colorSpec) {
        return dyedHorseArmor(parseColor(colorSpec));
    }

    public static Color parseColor(String colorSpec) {
        if (colorSpec == null || colorSpec.isBlank()) {
            return Color.WHITE;
        }
        String raw = colorSpec.trim();
        if (raw.startsWith("&#") && raw.length() >= 8) {
            return hexColor(raw.substring(2, 8));
        }
        if (raw.startsWith("#") && raw.length() >= 7) {
            return hexColor(raw.substring(1, 7));
        }
        if (raw.startsWith("&x") || raw.startsWith("&X")) {
            
            String digits = raw.replace("&", "").replace("x", "").replace("X", "");
            if (digits.length() >= 6) {
                return hexColor(digits.substring(0, 6));
            }
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "RED" -> Color.fromRGB(0xFF, 0x00, 0x00);
            case "ORANGE" -> Color.fromRGB(0xFF, 0x67, 0x00);
            case "YELLOW" -> Color.fromRGB(0xFC, 0xFF, 0x00);
            case "LIME" -> Color.fromRGB(0x94, 0xFF, 0x00);
            case "GREEN" -> Color.fromRGB(0x00, 0xC8, 0x53);
            case "LIGHT_BLUE", "LIGHTBLUE", "AQUA" -> Color.fromRGB(0x00, 0x8D, 0xFF);
            case "BLUE" -> Color.fromRGB(0x00, 0x55, 0xFF);
            case "CYAN" -> Color.fromRGB(0x00, 0xFF, 0xFF);
            case "PURPLE" -> Color.fromRGB(0x94, 0x00, 0xFF);
            case "PINK", "MAGENTA" -> Color.fromRGB(0xFF, 0x00, 0x67);
            case "WHITE" -> Color.WHITE;
            case "BLACK" -> Color.BLACK;
            case "GRAY", "GREY" -> Color.GRAY;
            default -> {
                if (raw.matches("(?i)[0-9A-F]{6}")) {
                    yield hexColor(raw);
                }
                yield Color.WHITE;
            }
        };
    }

    private static Color hexColor(String hex) {
        try {
            int value = Integer.parseInt(hex, 16);
            return Color.fromRGB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF);
        } catch (NumberFormatException ex) {
            return Color.WHITE;
        }
    }

    
    public static ItemBuilder harnessOrDyedArmor(String preferred, String colorSpec) {
        Material harness = MaterialUtil.resolve(preferred);
        if (harness != null && !harness.name().endsWith("_DYE")) {
            return of(harness);
        }
        return dyedHorseArmor(colorSpec);
    }

    public static ItemBuilder harnessOrDyedArmor(String preferred, String fallback, String colorSpec) {
        Material material = MaterialUtil.resolveWithFallbacks(preferred, fallback);
        if (material == null || material.name().endsWith("_DYE")) {
            return dyedHorseArmor(colorSpec);
        }
        if (material == Material.LEATHER_HORSE_ARMOR) {
            return dyedHorseArmor(colorSpec);
        }
        return of(material);
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.itemComponent(name));
            meta.itemName(null);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = new ArrayList<>();
            for (String line : lines) {
                lore.add(TextUtil.itemComponent(line));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(String... lines) {
        return lore(List.of(lines));
    }

    public ItemBuilder glow(boolean glow) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (glow) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.removeEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING);
            }
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder hideExtras() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP, ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        String typeName = item.getType().name();
        if (typeName.endsWith("BUNDLE") || typeName.endsWith("HARNESS")
                || item.getType() == Material.LEATHER_HORSE_ARMOR
                || item.getType() == Material.SADDLE) {
            hideExtras();
        }
        return item;
    }
}

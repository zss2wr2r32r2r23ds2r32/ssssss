package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
    }

    public ItemBuilder name(String legacyName) {
        return edit(meta -> meta.displayName(ColorUtil.parse(legacyName)));
    }

    public ItemBuilder lore(List<String> lines) {
        return edit(meta -> {
            List<Component> lore = new ArrayList<>();
            for (String line : lines) lore.add(ColorUtil.parse(line));
            meta.lore(lore);
        });
    }

    public ItemBuilder lore(String... lines) {
        return lore(List.of(lines));
    }

    public ItemBuilder glow(boolean glow) {
        return edit(meta -> {
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.removeEnchant(Enchantment.UNBREAKING);
            }
        });
    }

    public ItemBuilder hideAll() {
        return edit(meta -> meta.addItemFlags(ItemFlag.values()));
    }

    public ItemBuilder edit(java.util.function.Consumer<ItemMeta> consumer) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            consumer.accept(meta);
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        return item;
    }

    public static ItemStack fromSection(ConfigurationSection section) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }

        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }

        ItemStack stack = new ItemStack(material, section.getInt("amount", 1));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        if (section.contains("name")) {
            meta.displayName(ColorUtil.parse(section.getString("name", "")));
        }

        List<String> loreLines = section.getStringList("lore");
        if (!loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(ColorUtil.parse(line));
            }
            meta.lore(lore);
        }

        if (section.getBoolean("unbreakable", false)) {
            meta.setUnbreakable(true);
        }

        if (section.getBoolean("hide-flags", false)) {
            meta.addItemFlags(ItemFlag.values());
        }

        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            for (String key : enchantSection.getKeys(false)) {
                Enchantment enchantment = Enchantment.getByName(key.toUpperCase(Locale.ROOT));
                if (enchantment != null) {
                    meta.addEnchant(enchantment, enchantSection.getInt(key), true);
                }
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack fromMap(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return new ItemStack(Material.STONE);
        }
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        return fromSection(yaml);
    }
}

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
    private boolean glowing;

    public ItemBuilder(Material material) {
        this(new ItemStack(material == null ? Material.STONE : material));
    }

    public ItemBuilder(ItemStack base) {
        this.item = base == null ? new ItemStack(Material.STONE) : base.clone();
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && name != null) {
            meta.displayName(ColorUtil.parse(name));
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return this;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return this;
        }
        List<Component> lore = new ArrayList<>(lines.size());
        for (String line : lines) {
            lore.add(ColorUtil.parse(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glowing = glow;
        return this;
    }

    public ItemBuilder hideAll() {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return this;
    }

    public ItemStack build() {
        if (glowing) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item.clone();
    }

    public static ItemStack fromSection(ConfigurationSection section) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }

        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material, section.getInt("amount", 1));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
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

        item.setItemMeta(meta);
        return item;
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

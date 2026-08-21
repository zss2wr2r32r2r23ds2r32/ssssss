package com.shardedmc.lobbycore.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public final class ItemBuilder {

    private final ItemStack item;

    private ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder of(ItemStack item) {
        ItemBuilder builder = new ItemBuilder(item.getType());
        builder.item.setAmount(item.getAmount());
        if (item.hasItemMeta()) {
            builder.item.setItemMeta(item.getItemMeta());
        }
        return builder;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder name(String name) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MessageUtil.component(name));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.lore(lore.stream().map(MessageUtil::component).toList());
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder loreFormatted(List<String> lore, Player player) {
        return lore(MessageUtil.formatLore(lore, player));
    }

    public ItemBuilder enchant(Enchantment enchantment, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(flags);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder unbreakable() {
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(data);
        item.setItemMeta(meta);
        return this;
    }

    public ItemStack build() {
        return item.clone();
    }

    public static boolean matchesName(ItemStack item, String displayName) {
        if (item == null || displayName == null || displayName.isEmpty()) {
            return false;
        }
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }
        String itemPlain = MessageUtil.plainText(item.getItemMeta().displayName());
        String configPlain = MessageUtil.plainText(displayName);
        return itemPlain.equalsIgnoreCase(configPlain);
    }

    public static boolean matchesMaterial(ItemStack item, Material material) {
        return item != null && material != null && item.getType() == material;
    }

    public static ItemStack fromConfig(org.bukkit.configuration.ConfigurationSection section, Player player) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemBuilder builder = ItemBuilder.of(material).amount(section.getInt("amount", 1));
        if (section.contains("name")) {
            builder.name(MessageUtil.format(section.getString("name"), player));
        }
        if (section.isList("lore")) {
            builder.lore(MessageUtil.formatLore(section.getStringList("lore"), player));
        }
        if (section.isConfigurationSection("enchantments")) {
            for (Map.Entry<String, Object> entry : section.getConfigurationSection("enchantments").getValues(false).entrySet()) {
                Enchantment enchantment = Enchantment.getByName(entry.getKey().toUpperCase());
                if (enchantment != null) {
                    builder.enchant(enchantment, (int) entry.getValue());
                }
            }
        }
        if (section.getBoolean("unbreakable", false)) {
            builder.unbreakable();
        }
        if (section.contains("custom-model-data")) {
            builder.customModelData(section.getInt("custom-model-data"));
        }
        builder.flags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        return builder.build();
    }
}

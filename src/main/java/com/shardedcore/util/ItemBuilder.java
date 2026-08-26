package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
    }

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
    }

    public ItemBuilder name(String legacyName) {
        return edit(meta -> meta.displayName(Text.c(legacyName)));
    }

    public ItemBuilder lore(List<String> lines) {
        return edit(meta -> {
            List<Component> lore = new ArrayList<>();
            for (String line : lines) lore.add(Text.c(line));
            meta.lore(lore);
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
}

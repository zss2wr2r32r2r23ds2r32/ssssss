package com.shardedcore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class Items {

    private Items() {
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemBuilder builder = new ItemBuilder(material).name(name);
        if (lore != null) builder.lore(lore);
        return builder.build();
    }

    public static ItemStack fromSection(ConfigurationSection section, Player player, String... pairs) {
        if (section == null) return new ItemStack(Material.STONE);
        Material material = Sounds.material(section.getString("material", "STONE"), Material.STONE);
        ItemBuilder builder = new ItemBuilder(material);
        String name = section.getString("name");
        if (name != null) {
            name = Text.apply(name, pairs);
            if (player != null) name = Text.applyPlaceholders(name, player);
            builder.name(name);
        }
        List<String> lore = section.getStringList("lore");
        if (!lore.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String line : lore) {
                line = Text.apply(line, pairs);
                if (player != null) line = Text.applyPlaceholders(line, player);
                out.add(line);
            }
            builder.lore(out);
        }
        if (section.getBoolean("glow", false)) builder.glow(true);
        builder.hideAll();
        return builder.build();
    }

    public static ItemStack head(Player player, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull && player != null) {
            skull.setOwningPlayer(player);
            skull.displayName(ColorUtil.parse(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> components = new ArrayList<>();
                for (String line : lore) components.add(ColorUtil.parse(line));
                skull.lore(components);
            }
            skull.addItemFlags(ItemFlag.values());
            item.setItemMeta(skull);
        }
        return item;
    }

    public static final class ItemBuilder {
        private final ItemStack item;

        public ItemBuilder(Material material) {
            this.item = new ItemStack(material == null ? Material.STONE : material);
        }

        public ItemBuilder(ItemStack stack) {
            this.item = stack == null ? new ItemStack(Material.STONE) : stack.clone();
        }

        public ItemBuilder name(String name) {
            return edit(meta -> meta.displayName(ColorUtil.parse(name)));
        }

        public ItemBuilder lore(List<String> lines) {
            return edit(meta -> {
                if (lines == null) return;
                List<Component> lore = new ArrayList<>();
                for (String line : lines) lore.add(ColorUtil.parse(line));
                meta.lore(lore);
            });
        }

        public ItemBuilder glow(boolean glow) {
            return edit(meta -> {
                if (glow) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            });
        }

        public ItemBuilder hideAll() {
            return edit(meta -> meta.addItemFlags(ItemFlag.values()));
        }

        public ItemBuilder amount(int amount) {
            item.setAmount(Math.max(1, amount));
            return this;
        }

        public ItemBuilder edit(Consumer<ItemMeta> consumer) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                consumer.accept(meta);
                item.setItemMeta(meta);
            }
            return this;
        }

        public ItemStack build() {
            return item.clone();
        }
    }

    public static ItemStack apply(ItemStack stack, Map<String, String> placeholders) {
        if (stack == null || placeholders == null) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        if (meta.hasDisplayName() && meta.displayName() != null) {
            // names already components; skip
        }
        stack.setItemMeta(meta);
        return stack;
    }
}

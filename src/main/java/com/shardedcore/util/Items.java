package com.shardedcore.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class Items {

    private Items() {
    }

    public static ItemStack named(Material material, String name, List<String> lore) {
        ItemBuilder builder = new ItemBuilder(material).name(name);
        if (lore != null) builder.lore(lore);
        return builder.build();
    }

    public static ItemStack fromMaterial(String raw) {
        if (raw != null && raw.toLowerCase().startsWith("basehead-")) {
            return texturedHead(raw.substring("basehead-".length()));
        }
        return new ItemStack(Sounds.material(raw, Material.STONE));
    }

    public static String serialize(ItemStack item) {
        if (item == null || item.getType().isAir()) return "";
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("i", item);
        return yaml.saveToString();
    }

    public static ItemStack deserialize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(raw);
        } catch (Exception ex) {
            return null;
        }
        return yaml.getItemStack("i");
    }

    public static ItemStack fromSection(ConfigurationSection section, Player player, String... pairs) {
        if (section == null) return new ItemStack(Material.STONE);
        ItemBuilder builder = new ItemBuilder(fromMaterial(section.getString("material", "STONE")));
        String name = section.getString("name", section.getString("display-name", section.getString("display_name")));
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
        if (section.getInt("amount", 0) > 0) builder.amount(section.getInt("amount"));
        if (section.getInt("custom-model-data", 0) > 0) {
            int model = section.getInt("custom-model-data");
            builder.edit(meta -> meta.setCustomModelData(model));
        }
        builder.hideAll();
        return builder.build();
    }

    public static ItemStack head(Player player, String name, List<String> lore) {
        return head((OfflinePlayer) player, name, lore);
    }

    public static ItemStack head(OfflinePlayer player, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            if (player != null) skull.setOwningPlayer(player);
            skull.displayName(ColorUtil.parse(name));
            if (lore != null && !lore.isEmpty()) {
                List<Component> components = new ArrayList<>();
                for (String line : lore) components.add(ColorUtil.parse(line));
                skull.lore(components);
            }
            skull.addItemFlags(ItemFlag.values());
            item.setItemMeta(skull);
        }
        hideBundleBits(item);
        return item;
    }

    public static ItemStack texturedHead(String base64) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skull && base64 != null && !base64.isBlank()) {
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(base64.getBytes()));
            profile.setProperty(new ProfileProperty("textures", base64));
            skull.setPlayerProfile(profile);
            item.setItemMeta(skull);
        }
        hideBundleBits(item);
        return item;
    }

    public static ItemStack hideBundleBits(ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        try {
            if (item.getType().name().contains("BUNDLE")) {
                item.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(List.of()));
                item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                        .hiddenComponents(java.util.Set.of(DataComponentTypes.BUNDLE_CONTENTS))
                        .build());
            }
        } catch (Throwable ignored) {
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            try {
                meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            } catch (Throwable ignored) {
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public static List<String> lore(ConfigurationSection section, String path, List<String> fallback, String... pairs) {
        List<String> lines = section == null ? List.of() : section.getStringList(path);
        if (lines == null || lines.isEmpty()) lines = fallback == null ? List.of() : fallback;
        return Text.applyList(new ArrayList<>(lines), pairs);
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
            return hideBundleBits(item.clone());
        }
    }

    public static ItemStack apply(ItemStack stack, Map<String, String> placeholders) {
        if (stack == null || placeholders == null) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        stack.setItemMeta(meta);
        return hideBundleBits(stack);
    }
}

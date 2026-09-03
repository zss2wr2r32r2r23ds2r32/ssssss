package com.shardedcore.eventcore.config;

import com.shardedcore.eventcore.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A fully operator-editable GUI icon: material, slot, display name and lore are
 * all read from configuration so the look of every menu can be changed without
 * touching the plugin.
 */
public final class ItemDefinition {

    private final int slot;
    private final Material material;
    private final String name;
    private final List<String> lore;
    private final int amount;
    private final int customModelData;
    private final boolean glow;
    private final boolean glowWhenEnabled;
    private final boolean hideAttributes;

    private ItemDefinition(int slot, Material material, String name, List<String> lore, int amount,
                           int customModelData, boolean glow, boolean glowWhenEnabled, boolean hideAttributes) {
        this.slot = slot;
        this.material = material;
        this.name = name;
        this.lore = lore;
        this.amount = amount;
        this.customModelData = customModelData;
        this.glow = glow;
        this.glowWhenEnabled = glowWhenEnabled;
        this.hideAttributes = hideAttributes;
    }

    public static ItemDefinition of(ConfigurationSection section, Material fallbackMaterial, int fallbackSlot) {
        if (section == null) {
            return new ItemDefinition(fallbackSlot, fallbackMaterial, "", Collections.emptyList(), 1, -1,
                    false, true, true);
        }
        Material material = parseMaterial(section.getString("material"), fallbackMaterial);
        return new ItemDefinition(
                section.getInt("slot", fallbackSlot),
                material,
                section.getString("name", ""),
                section.getStringList("lore"),
                Math.max(1, section.getInt("amount", 1)),
                section.getInt("custom-model-data", -1),
                section.getBoolean("glow", false),
                section.getBoolean("glow-when-enabled", true),
                section.getBoolean("hide-attributes", true));
    }

    public static Material parseMaterial(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Material parsed = Material.matchMaterial(raw.trim().toUpperCase(Locale.ROOT));
        return parsed == null || parsed.isAir() ? fallback : parsed;
    }

    /**
     * Layers an optional {@code selected:} block over this icon.
     *
     * <p>Only the keys the operator actually set are replaced, so a menu can
     * change just the lore when an entry is selected and keep everything else.</p>
     */
    public ItemDefinition withOverlay(ConfigurationSection overlay) {
        if (overlay == null) {
            return this;
        }
        return new ItemDefinition(
                slot,
                overlay.isSet("material") ? parseMaterial(overlay.getString("material"), material) : material,
                overlay.isSet("name") ? overlay.getString("name", name) : name,
                overlay.isSet("lore") ? overlay.getStringList("lore") : lore,
                overlay.isSet("amount") ? Math.max(1, overlay.getInt("amount")) : amount,
                overlay.isSet("custom-model-data") ? overlay.getInt("custom-model-data") : customModelData,
                overlay.getBoolean("glow", glow),
                glowWhenEnabled,
                hideAttributes);
    }

    public int slot() {
        return slot;
    }

    public Material material() {
        return material;
    }

    public List<String> rawLore() {
        return lore;
    }

    public String rawName() {
        return name;
    }

    public ItemStack build(Map<String, String> placeholders) {
        return build(placeholders, false, material);
    }

    public ItemStack build(Map<String, String> placeholders, boolean enabled) {
        return build(placeholders, enabled, material);
    }

    /**
     * Renders the icon. {@code enabled} drives the optional enchantment glint so
     * an operator can see a toggle's state at a glance without reading the lore.
     */
    public ItemStack build(Map<String, String> placeholders, boolean enabled, Material overrideMaterial) {
        ItemStack stack = new ItemStack(overrideMaterial == null ? material : overrideMaterial, amount);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (!name.isEmpty()) {
            meta.displayName(Text.parse(name, placeholders));
        }
        if (!lore.isEmpty()) {
            List<Component> rendered = Text.parseLore(lore, placeholders);
            if (!rendered.isEmpty()) {
                meta.lore(rendered);
            }
        }
        if (customModelData >= 0) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of((float) customModelData));
            meta.setCustomModelDataComponent(component);
        }
        if (glow || (glowWhenEnabled && enabled)) {
            meta.setEnchantmentGlintOverride(true);
        }
        if (hideAttributes) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_DYE, ItemFlag.HIDE_UNBREAKABLE);
        }
        stack.setItemMeta(meta);
        return stack;
    }
}

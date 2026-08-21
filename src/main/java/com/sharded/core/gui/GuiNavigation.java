package com.sharded.core.gui;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.ConfigSync;
import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared back / previous / next / close button templates from gui-navigation.yml. */
public final class GuiNavigation {

    private YamlConfiguration config;

    public GuiNavigation(ShardedCore plugin) {
        reload(plugin);
    }

    public void reload(ShardedCore plugin) {
        File file = new File(plugin.getDataFolder(), "gui-navigation.yml");
        ConfigSync.sync(plugin, file, "gui-navigation.yml");
        config = YamlConfiguration.loadConfiguration(file);
    }

    public ItemStack build(String type) {
        return build(type, null);
    }

    public ItemStack build(String type, ConfigurationSection override) {
        ConfigurationSection base = section(type);
        if (base == null && override == null) {
            return new ItemBuilder(Material.BARRIER).name("&cMissing nav: " + type).build();
        }

        String materialRaw = pickString(override, base, "material", "STONE");
        ItemStack stack = HeadUtil.parse(materialRaw);
        if (stack == null) {
            Material mat = Material.matchMaterial(materialRaw.toUpperCase(Locale.ROOT));
            stack = new ItemStack(mat == null ? Material.STONE : mat);
        }

        String name = pickString(override, base, "display_name", pickString(override, base, "name", "&f" + type));
        List<String> lore = pickLore(override, base);

        return new ItemBuilder(stack).name(name).lore(lore).hideAll().build();
    }

    public String displayName(String type, ConfigurationSection override) {
        ConfigurationSection base = section(type);
        return pickString(override, base, "display_name", pickString(override, base, "name", "&f" + type));
    }

    public List<String> lore(String type, ConfigurationSection override) {
        return pickLore(override, section(type));
    }

    public Material material(String type, ConfigurationSection override) {
        ConfigurationSection base = section(type);
        Material mat = Material.matchMaterial(pickString(override, base, "material", "STONE").toUpperCase(Locale.ROOT));
        return mat == null ? Material.STONE : mat;
    }

    public ConfigurationSection section(String type) {
        return config == null ? null : config.getConfigurationSection(type.toLowerCase(Locale.ROOT));
    }

    private static String pickString(ConfigurationSection override, ConfigurationSection base, String key, String def) {
        if (override != null && override.contains(key)) return override.getString(key, def);
        if (base != null && base.contains(key)) return base.getString(key, def);
        return def;
    }

    private static List<String> pickLore(ConfigurationSection override, ConfigurationSection base) {
        if (override != null && !override.getStringList("lore").isEmpty()) {
            return new ArrayList<>(override.getStringList("lore"));
        }
        if (base != null && !base.getStringList("lore").isEmpty()) {
            return new ArrayList<>(base.getStringList("lore"));
        }
        return List.of();
    }

    public static String resolveNavType(String itemKey, ConfigurationSection item) {
        if (item != null && item.contains("nav")) {
            return item.getString("nav", "").toLowerCase(Locale.ROOT);
        }
        if (itemKey == null) return null;
        String key = itemKey.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "back_button", "back" -> "back";
            case "close_menu", "close", "cancel" -> "close";
            case "previous_button", "previous" -> "previous";
            case "next_button", "next" -> "next";
            default -> null;
        };
    }
}

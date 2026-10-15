package com.sharded.core.modules.shop;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.*;

public final class ShopCatalog {
    public record ShopItem(String key, Material material, long price, int page, int slot, String customName, List<String> customLore) {}
    public record Section(String id, YamlConfiguration config, List<ShopItem> items) {
        String title() { return config.getString("menu.title", "&8Shop"); }
        int rows() { return config.getInt("menu.rows", 6); }
        int pages() { return config.getInt("menu.pages", 1); }
    }
    private final YamlConfiguration mainConfig, buyingConfig;
    private final Map<String, Section> sections = new LinkedHashMap<>();

    ShopCatalog(File shopFolder) {
        mainConfig = YamlConfiguration.loadConfiguration(new File(shopFolder, "config.yml"));
        buyingConfig = YamlConfiguration.loadConfiguration(new File(shopFolder, "buyingmenu.yml"));
        ConfigurationSection sectionRoot = mainConfig.getConfigurationSection("sections");
        if (sectionRoot == null) return;
        for (String id : sectionRoot.getKeys(false)) {
            if (!sectionRoot.getBoolean(id + ".enabled", true)) continue;
            File sectionFile = new File(shopFolder, id + "/config.yml");
            if (!sectionFile.exists()) continue;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(sectionFile);
            sections.put(id, new Section(id, yaml, loadItems(yaml)));
        }
    }
    private List<ShopItem> loadItems(YamlConfiguration yaml) {
        List<ShopItem> items = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("items");
        if (root == null) return items;
        for (String key : root.getKeys(false)) {
            ConfigurationSection item = root.getConfigurationSection(key);
            if (item == null) continue;
            Material mat = Material.matchMaterial(item.getString("material", "STONE").toUpperCase(Locale.ROOT));
            if (mat == null) continue;
            items.add(new ShopItem(key, mat, item.getLong("price", 0), item.getInt("page", 1), item.getInt("slot", 0), item.getString("name"), item.getStringList("lore")));
        }
        return items;
    }
    YamlConfiguration mainConfig() { return mainConfig; }
    YamlConfiguration buyingConfig() { return buyingConfig; }
    Map<String, Section> sections() { return sections; }
    Section section(String id) { return sections.get(id); }
    int mainRows() { return mainConfig.getInt("menu.rows", 4); }
    String mainTitle() { return mainConfig.getString("menu.title", "&8Shop Menu"); }
}

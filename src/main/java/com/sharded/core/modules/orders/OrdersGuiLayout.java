package com.sharded.core.modules.orders;

import com.sharded.core.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.io.File;
import java.util.*;

final class OrdersGuiLayout {
    record Button(int slot, ItemStack item) {}
    private final YamlConfiguration yaml;
    OrdersGuiLayout(File file) { this.yaml = YamlConfiguration.loadConfiguration(file); }
    String title() { return yaml.getString("title", "&8Orders"); }
    int size() { return Math.max(9, Math.min(54, yaml.getInt("size", 27))); }
    int slot(String key) { ConfigurationSection s = yaml.getConfigurationSection(key); return s == null ? -1 : s.getInt("slot", -1); }
    Button button(String key) { return button(key, Map.of()); }
    Button button(String key, Map<String, String> ph) {
        ConfigurationSection s = yaml.getConfigurationSection(key);
        if (s == null) return null;
        int slot = s.getInt("slot", -1);
        return slot < 0 ? null : new Button(slot, buildItem(s, ph));
    }
    ItemStack buildItem(ConfigurationSection s, Map<String, String> ph) {
        Material mat = Material.matchMaterial(s.getString("material", "STONE").toUpperCase());
        if (mat == null) mat = Material.STONE;
        int amount = Math.max(1, s.getInt("amount", 1));
        ItemStack built = new ItemBuilder(mat).name(apply(s.getString("display_name", s.getString("name", " ")), ph))
                .lore(s.getStringList("lore").stream().map(l -> apply(l, ph)).toList())
                .glow(s.getBoolean("glow", false)).hideAll().build();
        built.setAmount(amount);
        return built;
    }
    ItemStack orderItem(ConfigurationSection s, Map<String, String> ph) {
        if (s == null) return new ItemStack(Material.BARRIER);
        Material mat = Material.matchMaterial(s.getString("material", "CHEST").toUpperCase());
        if (mat == null) mat = Material.CHEST;
        return new ItemBuilder(mat).name(apply(s.getString("name", "&7Order"), ph))
                .lore(s.getStringList("lore").stream().map(l -> apply(l, ph)).toList())
                .glow(s.getBoolean("glow", false)).hideAll().build();
    }
    ConfigurationSection section(String key) { return yaml.getConfigurationSection(key); }
    String raw(String key) { return yaml.getString(key, ""); }
    String raw(String key, Map<String, String> ph) { return apply(yaml.getString(key, ""), ph); }
    List<String> loreList(String key, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (String line : yaml.getStringList(key)) out.add(apply(line, ph));
        return out;
    }
    List<Integer> fillerSlots() { return yaml.getIntegerList("filler-slots"); }
    ItemStack filler() {
        ConfigurationSection s = yaml.getConfigurationSection("filler");
        return s == null ? new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").hideAll().build() : buildItem(s, Map.of());
    }
    int orderSlot() { return yaml.getInt("order-slot", 13); }
    List<Integer> contentSlots() { return List.of(0,1,2,3,4,5,6,7,8); }
    private static String apply(String input, Map<String, String> ph) {
        if (input == null) return "";
        String out = input;
        for (var e : ph.entrySet()) out = out.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        return out;
    }
}

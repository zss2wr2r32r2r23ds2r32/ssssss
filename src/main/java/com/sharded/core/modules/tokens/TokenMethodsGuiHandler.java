package com.sharded.core.modules.tokens;

import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** /tokenmethods GUI — best to worst ways to earn tokens. */
final class TokenMethodsGuiHandler {

    static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final TokensModule module;

    TokenMethodsGuiHandler(TokensModule module) {
        this.module = module;
    }

    void open(Player player) {
        String title = module.configString("gui.title", "&8Token Methods");
        Inventory inv = Bukkit.createInventory(new Holder(), 27, Text.c(title));
        ((Holder) inv.getHolder()).inventory = inv;
        fill(inv);

        List<Map.Entry<String, ConfigurationSection>> methods = sortedMethods();
        String click = module.configString("gui.click-footer",
                "&x&F&F&B&A&0&0▷ &x&F&F&B&A&0&0&l&nCLICK&r &x&F&F&B&A&0&0To View");

        int rank = 1;
        for (Map.Entry<String, ConfigurationSection> entry : methods) {
            ConfigurationSection m = entry.getValue();
            int slot = m.getInt("slot", 13);
            Material mat = Material.matchMaterial(m.getString("material", "PAPER"));
            if (mat == null) mat = Material.PAPER;
            List<String> lore = new ArrayList<>();
            for (String line : m.getStringList("lore")) {
                lore.add(line.replace("%click%", click)
                        .replace("%amount%", String.valueOf(m.getLong("amount", 50L)))
                        .replace("%rank%", String.valueOf(rank)));
            }
            String name = m.getString("name", entry.getKey());
            if (!name.contains("#")) {
                name = name + " &7(#" + rank + ")";
            }
            inv.setItem(slot, new ItemBuilder(mat)
                    .name(name.replace("%rank%", String.valueOf(rank)))
                    .lore(lore)
                    .build());
            rank++;
        }
        player.openInventory(inv);
    }

    void handleClick(Player player, int slot) {
        ConfigurationSection section = module.configSection("gui.methods");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection m = section.getConfigurationSection(key);
            if (m == null || m.getInt("slot") != slot) continue;
            String cmd = m.getString("command", "");
            if (!cmd.isBlank()) player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            else if (key.equals("playtime")) player.performCommand("hourly");
            return;
        }
    }

    private List<Map.Entry<String, ConfigurationSection>> sortedMethods() {
        ConfigurationSection section = module.configSection("gui.methods");
        List<Map.Entry<String, ConfigurationSection>> list = new ArrayList<>();
        if (section == null) return list;
        for (String key : section.getKeys(false)) {
            ConfigurationSection m = section.getConfigurationSection(key);
            if (m != null) list.add(Map.entry(key, m));
        }
        list.sort(Comparator.comparingInt(e -> e.getValue().getInt("order", e.getValue().getInt("slot", 99))));
        return list;
    }

    private static void fill(Inventory inv) {
        ItemStack pane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }
}

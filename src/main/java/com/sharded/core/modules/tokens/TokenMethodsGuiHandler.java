package com.sharded.core.modules.tokens;

import com.sharded.core.util.GuiFooters;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.PlaceholderUtil;
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

    private static final int[] RANK_SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};

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
        Inventory inv = Bukkit.createInventory(new Holder(), 36, Text.c(title));
        ((Holder) inv.getHolder()).inventory = inv;
        fill(inv);

        List<Map.Entry<String, ConfigurationSection>> methods = sortedMethods();
        String click = module.configString("gui.click-footer", GuiFooters.view());

        int rank = 1;
        int slotIndex = 0;
        for (Map.Entry<String, ConfigurationSection> entry : methods) {
            if (slotIndex >= RANK_SLOTS.length) break;
            ConfigurationSection m = entry.getValue();
            int slot = RANK_SLOTS[slotIndex++];
            Material mat = Material.matchMaterial(m.getString("material", "PAPER"));
            if (mat == null) mat = Material.PAPER;
            List<String> lore = new ArrayList<>();
            for (String line : m.getStringList("lore")) {
                lore.add(PlaceholderUtil.apply(player, line.replace("%click%", click)
                        .replace("%amount%", String.valueOf(m.getLong("amount", 50L)))
                        .replace("%rank%", String.valueOf(rank))));
            }
            String name = PlaceholderUtil.apply(player, m.getString("name", entry.getKey())
                    .replace("%rank%", String.valueOf(rank)));
            inv.setItem(slot, new ItemBuilder(mat)
                    .name(name)
                    .lore(lore)
                    .build());
            rank++;
        }
        player.openInventory(inv);
    }

    void handleClick(Player player, int slot) {
        ConfigurationSection section = module.configSection("gui.methods");
        if (section == null) return;
        List<Map.Entry<String, ConfigurationSection>> methods = sortedMethods();
        for (int i = 0; i < methods.size() && i < RANK_SLOTS.length; i++) {
            if (RANK_SLOTS[i] != slot) continue;
            ConfigurationSection m = methods.get(i).getValue();
            String cmd = m.getString("command", "");
            if (!cmd.isBlank()) {
                player.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
            } else if (methods.get(i).getKey().equals("playtime")) {
                player.performCommand("hourly");
            }
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
        list.sort(Comparator.comparingInt(e -> e.getValue().getInt("order", 99)));
        return list;
    }

    private static void fill(Inventory inv) {
        ItemStack pane = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }
}

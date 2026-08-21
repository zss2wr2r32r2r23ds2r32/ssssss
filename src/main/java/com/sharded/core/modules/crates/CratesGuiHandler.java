package com.sharded.core.modules.crates;

import com.sharded.core.ShardedCore;
import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** ExcellentCrates-linked crate preview/open GUI. */
final class CratesGuiHandler {

    static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private record CrateEntry(String name, List<String> lore, List<String> preview, List<String> open) {}

    private final CratesModule module;
    private final ShardedCore plugin;
    private String title = "&8Crates";
    private int size = 27;
    private final Map<Integer, ItemStack> icons = new HashMap<>();
    private final Map<Integer, CrateEntry> actions = new HashMap<>();

    CratesGuiHandler(CratesModule module, ShardedCore plugin) {
        this.module = module;
        this.plugin = plugin;
        reload();
    }

    void reload() {
        icons.clear();
        actions.clear();
        File file = new File(module.moduleFolder(), "gui.yml");
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        title = yaml.getString("menu_title", title);
        size = Math.max(9, Math.min(54, yaml.getInt("size", 27)));
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;
            List<Integer> slots = new ArrayList<>();
            if (item.contains("slot")) slots.add(item.getInt("slot"));
            if (item.contains("slots")) slots.addAll(item.getIntegerList("slots"));
            if (slots.isEmpty()) continue;

            String materialRaw = item.getString("material", "STONE");
            ItemStack stack = HeadUtil.parse(materialRaw);
            if (stack == null) {
                Material mat = Material.matchMaterial(materialRaw.toUpperCase());
                stack = new ItemStack(mat == null ? Material.STONE : mat);
            }
            stack = new ItemBuilder(stack).hideAll().build();
            CrateEntry entry = new CrateEntry(
                    item.getString("display_name", " "),
                    item.getStringList("lore"),
                    item.getStringList("left_click_commands"),
                    item.getStringList("right_click_commands"));
            for (int slot : slots) {
                icons.put(slot, stack.clone());
                actions.put(slot, entry);
            }
        }
    }

    void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), size, Text.c(apply(player, title)));
        ((Holder) inv.getHolder()).inventory = inv;
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        for (Map.Entry<Integer, ItemStack> e : icons.entrySet()) {
            CrateEntry entry = actions.get(e.getKey());
            if (entry == null) continue;
            inv.setItem(e.getKey(), buildItem(player, e.getValue(), entry));
        }
        player.openInventory(inv);
    }

    private ItemStack buildItem(Player player, ItemStack base, CrateEntry entry) {
        List<String> lore = new ArrayList<>();
        for (String line : entry.lore()) lore.add(apply(player, line));
        return new ItemBuilder(base.clone())
                .name(apply(player, entry.name()))
                .lore(lore)
                .build();
    }

    void handleClick(Player player, int slot, ClickType click) {
        CrateEntry entry = actions.get(slot);
        if (entry == null) return;
        List<String> commands = click != null && click.isRightClick() ? entry.open() : entry.preview();
        if (commands.isEmpty()) commands = entry.preview();
        for (String cmd : commands) run(player, cmd);
    }

    private void run(Player player, String line) {
        if (line == null || line.isBlank()) return;
        line = line.trim();
        if (line.startsWith("[player]")) {
            player.performCommand(line.substring(8).trim());
        } else if (line.startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    line.substring(9).trim().replace("%player_name%", player.getName()).replace("%player%", player.getName()));
        } else if (line.startsWith("[message]")) {
            player.sendMessage(Text.c(apply(player, line.substring(9).trim())));
        }
    }

    String apply(Player player, String input) {
        if (input == null) return "";
        String out = input.replace("%player_name%", player.getName()).replace("%player%", player.getName());
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            out = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, out);
        }
        return out;
    }
}

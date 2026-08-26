package com.shardedcore.modules.media;

import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.HeadUtil;
import com.shardedcore.util.ItemBuilder;
import com.shardedcore.util.Text;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class MediaGuiHandler {

    static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private record Entry(List<String> commands) {}

    private final MediaModule module;
    private final Map<Integer, Entry> actions = new HashMap<>();

    MediaGuiHandler(MediaModule module) { this.module = module; reload(); }

    void reload() {
        actions.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(module.guiFile());
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            ConfigurationSection item = section.getConfigurationSection(key);
            if (item == null) continue;
            int slot = item.getInt("slot", -1);
            if (slot >= 0) actions.put(slot, new Entry(item.getStringList("left_click_commands")));
        }
    }

    void open(Player player) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(module.guiFile());
        int size = Math.max(9, Math.min(54, yaml.getInt("size", 27)));
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size, ColorUtil.parse(yaml.getString("menu_title", "&8Media")));
        holder.inventory = inv;
        TrackedInventories.track(inv, holder);
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        ConfigurationSection section = yaml.getConfigurationSection("items");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection item = section.getConfigurationSection(key);
                if (item == null) continue;
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;
                ItemStack stack = HeadUtil.parse(item.getString("material", "PAPER"));
                if (stack == null) {
                    Material mat = Material.matchMaterial(item.getString("material", "PAPER").toUpperCase());
                    stack = new ItemStack(mat == null ? Material.PAPER : mat);
                }
                List<String> lore = new ArrayList<>();
                for (String line : item.getStringList("lore")) lore.add(Text.applyPlaceholders(line, player));
                inv.setItem(slot, new ItemBuilder(stack).hideAll()
                        .name(Text.applyPlaceholders(item.getString("display_name", key), player)).lore(lore).build());
            }
        }
        player.openInventory(inv);
    }

    void handleClick(InventoryClickEvent event) {
        if (TrackedInventories.lookup(event.getView().getTopInventory(), Holder.class) == null) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Entry entry = actions.get(event.getSlot());
        if (entry == null) return;
        for (String cmd : entry.commands()) run(player, cmd);
    }

    private void run(Player player, String line) {
        if (line == null || line.isBlank()) return;
        line = line.trim();
        if (line.startsWith("[player]")) player.performCommand(line.substring(8).trim());
        else if (line.startsWith("[console]")) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line.substring(9).trim().replace("%player%", player.getName()));
    }
}

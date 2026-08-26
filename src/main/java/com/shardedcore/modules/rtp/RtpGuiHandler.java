package com.shardedcore.modules.rtp;

import com.shardedcore.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;

import java.io.File;
import java.util.*;

final class RtpGuiHandler {

    static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final RtpModule module;
    private final Map<Integer, String> slots = new HashMap<>();

    RtpGuiHandler(RtpModule module) {
        this.module = module;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(module.moduleFolderPath(), "gui.yml"));
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item != null && item.getInt("slot", -1) >= 0) slots.put(item.getInt("slot"), item.getString("action", key));
        }
    }

    void open(Player player) {
        File guiFile = new File(module.moduleFolderPath(), "gui.yml");
        ConfigUtil.saveDefaultResource(module.plugin(), "modules/rtp/gui.yml", guiFile, false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(guiFile);
        int size = yaml.getInt("size", 27);
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, size, ColorUtil.parse(Text.applyPlaceholders(yaml.getString("menu_title", "&8RTP"), player)));
        holder.inventory = inv;
        TrackedInventories.track(inv, holder);
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        ConfigurationSection items = yaml.getConfigurationSection("items");
        if (items != null) for (String key : items.getKeys(false)) {
            ConfigurationSection item = items.getConfigurationSection(key);
            if (item == null) continue;
            int slot = item.getInt("slot", -1);
            if (slot < 0 || slot >= size) continue;
            Material mat = Material.matchMaterial(item.getString("material", "GRASS_BLOCK").toUpperCase());
            if (mat == null) mat = Material.GRASS_BLOCK;
            inv.setItem(slot, new ItemBuilder(mat).hideAll().name(Text.applyPlaceholders(item.getString("display_name", key), player))
                    .lore(item.getStringList("lore").stream().map(l -> Text.applyPlaceholders(l, player)).toList()).build());
        }
        player.openInventory(inv);
    }

    void handleClick(InventoryClickEvent event) {
        if (TrackedInventories.lookup(event.getView().getTopInventory(), Holder.class) == null) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String dest = slots.get(event.getSlot());
        if (dest != null) module.beginTeleport(player, dest);
    }
}

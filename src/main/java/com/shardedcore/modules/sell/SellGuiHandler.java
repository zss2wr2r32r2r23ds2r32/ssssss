package com.shardedcore.modules.sell;

import com.shardedcore.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.List;
import java.util.Map;

final class SellGuiHandler {
    enum MenuType { SELL, WORTH, MULTIPLIER }
    static final class SellGuiHolder implements InventoryHolder {
        final MenuType type; int page; Inventory inventory;
        SellGuiHolder(MenuType type, int page) { this.type = type; this.page = page; }
        public Inventory getInventory() { return inventory; }
    }
    private final SellModule module;
    SellGuiHandler(SellModule module) { this.module = module; }

    void openSell(Player player) {
        var cfg = module.moduleConfig().getConfigurationSection("gui.sell");
        int size = cfg.getInt("size", 54);
        SellGuiHolder holder = new SellGuiHolder(MenuType.SELL, 0);
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(cfg.getString("title", "&8Sell Items")));
        holder.inventory = inv;
        Material fillerMat = Material.matchMaterial(cfg.getString("filler.material", "GRAY_STAINED_GLASS_PANE"));
        ItemStack filler = new ItemBuilder(fillerMat == null ? Material.GRAY_STAINED_GLASS_PANE : fillerMat).name(" ").hideAll().build();
        for (int i = 45; i < size; i++) inv.setItem(i, filler.clone());
        int confirmSlot = cfg.getInt("confirm-slot", 49);
        inv.setItem(confirmSlot, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name(cfg.getString("confirm.name", "&aConfirm")).lore(cfg.getStringList("confirm.lore")).hideAll().build());
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void openWorth(Player player, int page) {
        YamlConfiguration worth = module.worthConfig();
        List<Integer> slots = worth.getIntegerList("entry-slots");
        if (slots.isEmpty()) for (int row = 1; row <= 4; row++) for (int col = 1; col <= 7; col++) slots.add(row * 9 + col);
        List<Map.Entry<Material, Long>> entries = module.prices().sortedEntries();
        int pageSize = slots.size(), maxPage = Math.max(0, (entries.size()-1)/pageSize);
        page = Math.max(0, Math.min(page, maxPage));
        SellGuiHolder holder = new SellGuiHolder(MenuType.WORTH, page);
        Inventory inv = Bukkit.createInventory(holder, worth.getInt("size", 54), Text.c(worth.getString("title", "&8Worth").replace("%page%", String.valueOf(page+1))));
        holder.inventory = inv;
        double mult = module.prices().multiplier(player);
        for (int i = 0; i < slots.size() && page*pageSize+i < entries.size(); i++) {
            var e = entries.get(page*pageSize+i);
            inv.setItem(slots.get(i), new ItemBuilder(e.getKey()).name(worth.getString("item.name","%item%").replace("%item%", ItemStackUtil.formatMaterial(e.getKey())))
                    .lore(worth.getStringList("item.lore").stream().map(l -> l.replace("%item%", ItemStackUtil.formatMaterial(e.getKey())).replace("%price%", module.formatMoney(e.getValue())).replace("%multiplied%", module.formatMoney((long)(e.getValue()*mult)))).toList()).hideAll().build());
        }
        if (page > 0) inv.setItem(worth.getInt("previous-slot", 45), new ItemBuilder(Material.ARROW).name(worth.getString("previous.name","Prev")).hideAll().build());
        if (page*pageSize+pageSize < entries.size()) inv.setItem(worth.getInt("next-slot", 53), new ItemBuilder(Material.ARROW).name(worth.getString("next.name","Next")).hideAll().build());
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void openMultiplier(Player player) {
        YamlConfiguration mult = module.multiplierConfig();
        SellGuiHolder holder = new SellGuiHolder(MenuType.MULTIPLIER, 0);
        Inventory inv = Bukkit.createInventory(holder, mult.getInt("gui.size", 27), Text.c(mult.getString("gui.title", "&8Multipliers")));
        holder.inventory = inv;
        double current = module.prices().multiplier(player);
        var c = mult.getConfigurationSection("gui.current");
        if (c != null) inv.setItem(c.getInt("slot",13), new ItemBuilder(Material.GOLD_INGOT).name(c.getString("name","%multiplier%x").replace("%multiplier%", String.valueOf(current))).hideAll().build());
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void handleClick(Player player, SellGuiHolder holder, int slot) {
        if (holder.type == MenuType.SELL && slot == module.moduleConfig().getInt("gui.sell.confirm-slot", 49)) module.processSell(player, holder.inventory);
        else if (holder.type == MenuType.WORTH) {
            if (slot == module.worthConfig().getInt("previous-slot", 45)) openWorth(player, holder.page-1);
            if (slot == module.worthConfig().getInt("next-slot", 53)) openWorth(player, holder.page+1);
        }
    }
}

package com.sharded.core.modules.shop;

import com.sharded.core.util.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.*;

final class ShopGuiHandler {
    enum MenuType { MAIN, SECTION, BUY }
    static final class ShopGuiHolder implements InventoryHolder {
        final MenuType type; final String sectionId; final String itemKey; int page; int amount; Inventory inventory;
        ShopGuiHolder(MenuType type, String sectionId, String itemKey, int page, int amount) {
            this.type = type; this.sectionId = sectionId; this.itemKey = itemKey; this.page = page; this.amount = amount;
        }
        public Inventory getInventory() { return inventory; }
    }
    private final ShopModule module;
    ShopGuiHandler(ShopModule module) { this.module = module; }

    void openMain(Player player) {
        ShopCatalog catalog = module.catalog();
        if (catalog.sections().isEmpty()) { module.send(player,"no-sections"); return; }
        ShopGuiHolder holder = new ShopGuiHolder(MenuType.MAIN, "", "", 0, 1);
        Inventory inv = Bukkit.createInventory(holder, catalog.mainRows()*9, Text.c(catalog.mainTitle()));
        holder.inventory = inv;
        ConfigurationSection sections = catalog.mainConfig().getConfigurationSection("sections");
        for (Map.Entry<String, ShopCatalog.Section> entry : catalog.sections().entrySet()) {
            ConfigurationSection meta = sections.getConfigurationSection(entry.getKey());
            if (meta == null) continue;
            String perm = meta.getString("permission", "");
            if (!perm.isBlank() && !player.hasPermission(perm.startsWith("sharded.") ? perm : "sharded."+perm)) continue;
            ConfigurationSection icon = meta.getConfigurationSection("icon"); if (icon == null) continue;
            Material mat = Material.matchMaterial(icon.getString("material","CHEST"));
            inv.setItem(meta.getInt("slot",0), new ItemBuilder(mat==null?Material.CHEST:mat).name(icon.getString("name",entry.getKey())).lore(icon.getStringList("lore")).hideAll().build());
        }
        module.playConfiguredSound(player,"open"); TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void openSection(Player player, String sectionId, int page) {
        ShopCatalog.Section section = module.catalog().section(sectionId); if (section == null) return;
        ShopGuiHolder holder = new ShopGuiHolder(MenuType.SECTION, sectionId, "", page, 1);
        Inventory inv = Bukkit.createInventory(holder, section.rows()*9, Text.c(section.title()));
        holder.inventory = inv;
        fillButtons(inv, section, page);
        for (ShopCatalog.ShopItem item : section.items()) if (item.page() == page+1) inv.setItem(item.slot(), display(item));
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void openBuy(Player player, String sectionId, String itemKey) {
        ShopCatalog.Section section = module.catalog().section(sectionId);
        ShopCatalog.ShopItem shopItem = section.items().stream().filter(i -> i.key().equals(itemKey)).findFirst().orElse(null);
        if (shopItem == null) return;
        var buyCfg = module.catalog().buyingConfig();
        ShopGuiHolder holder = new ShopGuiHolder(MenuType.BUY, sectionId, itemKey, 0, 1);
        Inventory inv = Bukkit.createInventory(holder, buyCfg.getInt("rows",3)*9, Text.c(buyCfg.getString("title","&8Buying")));
        holder.inventory = inv; refreshBuy(inv, holder, shopItem, buyCfg);
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void handleClick(Player player, ShopGuiHolder holder, int slot) {
        switch (holder.type) {
            case MAIN -> {
                ConfigurationSection sections = module.catalog().mainConfig().getConfigurationSection("sections");
                for (String id : sections.getKeys(false)) {
                    ConfigurationSection meta = sections.getConfigurationSection(id);
                    if (meta != null && meta.getInt("slot",-1)==slot) {
                        String cmd = meta.getString("command","");
                        if (!cmd.isBlank()) { player.performCommand(cmd); return; }
                        if (module.catalog().section(id) != null) openSection(player,id,0);
                        return;
                    }
                }
            }
            case SECTION -> handleSection(player, holder, slot);
            case BUY -> handleBuy(player, holder, slot);
        }
    }

    private void handleSection(Player player, ShopGuiHolder holder, int slot) {
        ShopCatalog.Section section = module.catalog().section(holder.sectionId);
        ConfigurationSection buttons = section.config().getConfigurationSection("buttons");
        if (buttons != null) {
            ConfigurationSection prev = buttons.getConfigurationSection("previous");
            if (prev != null && slot == prev.getInt("slot",48) && holder.page > 0) { openSection(player, holder.sectionId, holder.page-1); return; }
            ConfigurationSection next = buttons.getConfigurationSection("next");
            if (next != null && slot == next.getInt("slot",50)) {
                int max = section.pages()-1; for (var i : section.items()) max = Math.max(max, i.page()-1);
                if (holder.page < max) openSection(player, holder.sectionId, holder.page+1); return;
            }
            ConfigurationSection ret = buttons.getConfigurationSection("return");
            if (ret != null && slot == ret.getInt("slot",49)) { openMain(player); return; }
        }
        for (ShopCatalog.ShopItem item : section.items()) if (item.page()==holder.page+1 && item.slot()==slot) { openBuy(player, holder.sectionId, item.key()); return; }
    }

    private void handleBuy(Player player, ShopGuiHolder holder, int slot) {
        ShopCatalog.Section section = module.catalog().section(holder.sectionId);
        ShopCatalog.ShopItem shopItem = section.items().stream().filter(i -> i.key().equals(holder.itemKey)).findFirst().orElse(null);
        if (shopItem == null) return;
        var buyCfg = module.catalog().buyingConfig(); int max = buyCfg.getInt("max-amount",64);
        ConfigurationSection buttons = buyCfg.getConfigurationSection("buttons");
        if (buttons == null) return;
        for (String key : buttons.getKeys(false)) {
            ConfigurationSection btn = buttons.getConfigurationSection(key);
            if (btn == null || btn.getInt("slot",-1) != slot) continue;
            switch (key) {
                case "set-min" -> holder.amount = 1;
                case "set-max" -> holder.amount = max;
                case "remove-10" -> holder.amount = Math.max(1, holder.amount - btn.getInt("amount",10));
                case "remove-1" -> holder.amount = Math.max(1, holder.amount - 1);
                case "add-1" -> holder.amount = Math.min(max, holder.amount + 1);
                case "add-10" -> holder.amount = Math.min(max, holder.amount + btn.getInt("amount",10));
                case "cancel" -> { openSection(player, holder.sectionId, 0); return; }
                case "confirm" -> { module.purchase(player, holder.sectionId, shopItem, holder.amount); return; }
            }
            refreshBuy(holder.inventory, holder, shopItem, buyCfg); return;
        }
    }

    private void refreshBuy(Inventory inv, ShopGuiHolder holder, ShopCatalog.ShopItem shopItem, org.bukkit.configuration.file.YamlConfiguration buyCfg) {
        inv.clear(); long total = shopItem.price()*holder.amount;
        ItemStack center = new ItemBuilder(shopItem.material()).name(buyCfg.getString("item.name","%item%").replace("%item%", ItemStackUtil.formatMaterial(shopItem.material())).replace("%amount%",String.valueOf(holder.amount)).replace("%total%",module.formatMoney(total))).hideAll().build();
        center.setAmount(Math.max(1, Math.min(64, holder.amount)));
        inv.setItem(buyCfg.getInt("item.slot",13), center);
        ConfigurationSection buttons = buyCfg.getConfigurationSection("buttons");
        if (buttons == null) return;
        for (String key : buttons.getKeys(false)) {
            ConfigurationSection btn = buttons.getConfigurationSection(key); if (btn == null) continue;
            Material mat = Material.matchMaterial(btn.getString("material","GLASS_PANE"));
            ItemStack pane = new ItemBuilder(mat==null?Material.GLASS_PANE:mat).name(btn.getString("name",key).replace("%total%",module.formatMoney(total))).hideAll().build();
            pane.setAmount(Math.max(1, Math.min(64, btn.getInt("amount",1))));
            inv.setItem(btn.getInt("slot",0), pane);
        }
    }

    private void fillButtons(Inventory inv, ShopCatalog.Section section, int page) {
        ConfigurationSection buttons = section.config().getConfigurationSection("buttons"); if (buttons == null) return;
        for (String key : List.of("previous","return","next")) {
            ConfigurationSection btn = buttons.getConfigurationSection(key); if (btn == null) continue;
            if (key.equals("previous") && page <= 0) continue;
            if (key.equals("next")) { int max = section.pages()-1; for (var i : section.items()) max = Math.max(max,i.page()-1); if (page >= max) continue; }
            Material mat = Material.matchMaterial(btn.getString("material","ARROW"));
            inv.setItem(btn.getInt("slot",0), new ItemBuilder(mat==null?Material.ARROW:mat).name(btn.getString("name",key)).lore(btn.getStringList("lore")).hideAll().build());
        }
    }

    private ItemStack display(ShopCatalog.ShopItem item) {
        var template = module.catalog().mainConfig().getConfigurationSection("item-template");
        String name = item.customName()!=null ? item.customName() : template.getString("name","%item%").replace("%item%", ItemStackUtil.formatMaterial(item.material()));
        List<String> lore = !item.customLore().isEmpty() ? item.customLore() : template.getStringList("lore");
        return new ItemBuilder(item.material()).name(name).lore(lore.stream().map(l -> l.replace("%item%", ItemStackUtil.formatMaterial(item.material())).replace("%price%", module.formatMoney(item.price()))).toList()).hideAll().build();
    }
}

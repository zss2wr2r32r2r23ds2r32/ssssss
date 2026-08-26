package com.shardedcore.modules.kits;

import com.shardedcore.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import java.util.List;
import java.util.Map;

final class KitsGuiHandler {
    enum MenuType { MAIN, PREVIEW, LAYOUT }
    static final class KitsGuiHolder implements InventoryHolder {
        final MenuType type; final String kitName; final boolean editing; Inventory inventory;
        KitsGuiHolder(MenuType type, String kitName, boolean editing) { this.type = type; this.kitName = kitName; this.editing = editing; }
        public Inventory getInventory() { return inventory; }
    }
    private final KitsModule module;
    KitsGuiHandler(KitsModule module) { this.module = module; }

    void openMain(Player player) {
        int size = module.moduleConfig().getInt("gui.main.size", 54);
        KitsGuiHolder holder = new KitsGuiHolder(MenuType.MAIN, "", false);
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(module.moduleConfig().getString("gui.main.title", "&8Kits")));
        holder.inventory = inv;
        ItemStack filler = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").hideAll().build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler.clone());
        for (KitsModule.KitDefinition kit : module.allKits()) {
            if (!kit.permission().isBlank() && !player.hasPermission(kit.permission())) continue;
            Material icon = Material.matchMaterial(kit.iconMaterial()); if (icon == null) icon = Material.CHEST;
            long rem = module.remainingCooldown(player, kit.name(), kit.cooldownSeconds());
            List<String> lore = new java.util.ArrayList<>(kit.lore()); lore.add(""); lore.add(rem > 0 ? "&cCooldown: &f"+Text.time(rem/1000) : "&aReady to claim");
            inv.setItem(kit.slot(), new ItemBuilder(icon).name(kit.displayName()).lore(lore).hideAll().build());
        }
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void openPreview(Player player, String kitName) {
        KitsModule.KitDefinition kit = module.kit(kitName); if (kit == null) return;
        int size = module.moduleConfig().getInt("gui.preview.size", 54);
        KitsGuiHolder holder = new KitsGuiHolder(MenuType.PREVIEW, kit.name(), false);
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(module.moduleConfig().getString("gui.preview.title","&8Preview").replace("%kit%", kit.displayName())));
        holder.inventory = inv;
        for (Map.Entry<Integer, ItemStack> e : kit.items().entrySet()) inv.setItem(e.getKey(), e.getValue().clone());
        inv.setItem(size-5, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&#FFD900&lCLAIM KIT").hideAll().build());
        inv.setItem(size-9, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&#FF0000&lBACK").hideAll().build());
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void openLayout(Player player, String kitName) {
        int size = module.moduleConfig().getInt("gui.layout.size", 54);
        KitsGuiHolder holder = new KitsGuiHolder(MenuType.LAYOUT, kitName.toLowerCase(), true);
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(module.moduleConfig().getString("gui.layout.title","&8Layout").replace("%kit%", kitName)));
        holder.inventory = inv;
        KitsModule.KitDefinition existing = module.kit(kitName);
        if (existing != null) for (Map.Entry<Integer, ItemStack> e : existing.items().entrySet()) inv.setItem(e.getKey(), e.getValue().clone());
        inv.setItem(size-5, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&#FFD900&lSAVE LAYOUT").hideAll().build());
        TrackedInventories.track(inv, holder); player.openInventory(inv);
    }

    void handleClick(Player player, KitsGuiHolder holder, int slot) {
        if (holder.type == MenuType.MAIN) {
            for (KitsModule.KitDefinition kit : module.allKits()) if (kit.slot() == slot) { openPreview(player, kit.name()); return; }
        } else if (holder.type == MenuType.PREVIEW) {
            int size = holder.inventory.getSize();
            if (slot == size-9) openMain(player);
            else if (slot == size-5) module.claimKit(player, holder.kitName);
        } else if (holder.type == MenuType.LAYOUT && slot == holder.inventory.getSize()-5) {
            java.util.HashMap<Integer, ItemStack> items = new java.util.HashMap<>();
            for (int i = 0; i < holder.inventory.getSize()-9; i++) {
                ItemStack s = holder.inventory.getItem(i); if (s != null && !s.getType().isAir()) items.put(i, s.clone());
            }
            module.saveLayout(player, holder.kitName, items); player.closeInventory();
        }
    }
}

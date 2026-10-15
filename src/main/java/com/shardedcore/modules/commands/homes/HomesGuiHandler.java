package com.shardedcore.modules.commands.homes;

import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.GuiUtil;
import com.shardedcore.util.Text;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class HomesGuiHandler {

    enum MenuType { MAIN, DELETE_CONFIRM }

    static final class HomesGuiHolder implements InventoryHolder {
        final MenuType type;
        final int slot;
        Inventory inventory;

        HomesGuiHolder(MenuType type, int slot) {
            this.type = type;
            this.slot = slot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final HomesModule module;

    HomesGuiHandler(HomesModule module) {
        this.module = module;
    }

    void openMain(Player player) {
        int size = Math.max(9, Math.min(54, module.config().getInt("gui.size", 54)));
        HomesGuiHolder holder = new HomesGuiHolder(MenuType.MAIN, 0);
        Inventory inv = Bukkit.createInventory(holder, size, ColorUtil.parse(module.guiRaw("main-title")));
        holder.inventory = inv;
        fill(inv);
        for (int slot = 1; slot <= module.maxSlotCount(); slot++) {
            Integer guiSlot = module.guiSlotFor(slot);
            if (guiSlot == null || guiSlot < 0 || guiSlot >= size) continue;
            HomesDatabase.Home home = module.database().getHome(player.getUniqueId(), slot);
            inv.setItem(guiSlot, homeItem(slot, home, slot <= module.maxHomes(player)));
        }
        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void openDeleteConfirm(Player player, int slot) {
        HomesGuiHolder holder = new HomesGuiHolder(MenuType.DELETE_CONFIRM, slot);
        Inventory inv = Bukkit.createInventory(holder, 27,
                ColorUtil.parse(module.guiRaw("delete-title", "%slot%", String.valueOf(slot))));
        holder.inventory = inv;
        fill(inv);
        inv.setItem(11, GuiUtil.item(Material.RED_CANDLE, module.guiRaw("delete-cancel-name"), module.guiRawList("delete-cancel-lore")));
        inv.setItem(13, GuiUtil.item(Material.RED_BED, module.guiRaw("delete-confirm-name", "%slot%", String.valueOf(slot)),
                module.guiRawList("delete-confirm-lore", "%slot%", String.valueOf(slot))));
        inv.setItem(15, GuiUtil.item(Material.LIME_CANDLE, module.guiRaw("delete-yes-name"), module.guiRawList("delete-yes-lore")));
        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void handleClick(Player player, HomesGuiHolder holder, int clickedSlot, org.bukkit.event.inventory.ClickType click) {
        if (holder.type == MenuType.MAIN) handleMainClick(player, clickedSlot, click);
        else handleDeleteClick(player, holder.slot, clickedSlot);
    }

    private void handleMainClick(Player player, int clickedSlot, org.bukkit.event.inventory.ClickType click) {
        Integer homeSlot = module.homeSlotForGuiSlot(clickedSlot);
        if (homeSlot == null) return;
        if (homeSlot > module.maxHomes(player)) {
            module.sendMessage(player, "slot-locked", "slot", String.valueOf(homeSlot), "max", String.valueOf(module.maxHomes(player)));
            return;
        }
        HomesDatabase.Home home = module.database().getHome(player.getUniqueId(), homeSlot);
        if (home == null) {
            module.sendMessage(player, "empty-slot", "slot", String.valueOf(homeSlot));
            return;
        }
        if (click == org.bukkit.event.inventory.ClickType.SHIFT_LEFT || click == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
            openDeleteConfirm(player, homeSlot);
            return;
        }
        module.teleportToHome(player, home);
    }

    private void handleDeleteClick(Player player, int homeSlot, int clickedSlot) {
        if (clickedSlot == 11) {
            openMain(player);
            return;
        }
        if (clickedSlot == 15 || clickedSlot == 13) {
            module.database().deleteHome(player.getUniqueId(), homeSlot);
            module.sendMessage(player, "deleted", "slot", String.valueOf(homeSlot));
            openMain(player);
        }
    }

    private ItemStack homeItem(int slot, HomesDatabase.Home home, boolean unlocked) {
        ConfigurationSection section = module.config().getConfigurationSection(home == null ? "gui.empty-item" : "gui.set-item");
        Material material = Material.matchMaterial(section == null ? (home == null ? "GRAY_DYE" : "RED_BED")
                : section.getString("material", home == null ? "GRAY_DYE" : "RED_BED"));
        if (material == null) material = home == null ? Material.GRAY_DYE : Material.RED_BED;
        String world = home == null ? "-" : home.world();
        String coords = home == null ? "-" : home.x() + ", " + home.y() + ", " + home.z();
        String name = Text.apply(section == null ? "HOME %slot%" : section.getString("name", "HOME %slot%"),
                "%slot%", String.valueOf(slot), "%world%", world, "%coords%", coords);
        List<String> lore = new ArrayList<>();
        if (section != null) {
            for (String line : section.getStringList("lore")) {
                lore.add(Text.apply(line, "%slot%", String.valueOf(slot), "%world%", world, "%coords%", coords));
            }
        }
        if (!unlocked) lore.add(module.guiRaw("permission-required", "%slot%", String.valueOf(slot)));
        return GuiUtil.item(material, name, lore);
    }

    private void fill(Inventory inv) {
        ItemStack pane = GuiUtil.filler(module.config().getConfigurationSection("gui.filler"));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    static Location toLocation(HomesDatabase.Home home) {
        if (home == null) return null;
        World world = Bukkit.getWorld(home.world());
        if (world == null) return null;
        return new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
    }
}

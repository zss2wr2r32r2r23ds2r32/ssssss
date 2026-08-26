package com.sharded.core.modules.homes;

import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
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
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(module.guiRaw("main-title")));
        holder.inventory = inv;
        fill(inv);
        int max = module.maxHomes(player);
        for (int slot = 1; slot <= module.maxSlotCount(); slot++) {
            Integer guiSlot = module.guiSlotFor(slot);
            if (guiSlot == null || guiSlot < 0 || guiSlot >= size) continue;
            HomesDatabase.Home home = module.database().getHome(player.getUniqueId(), slot);
            boolean unlocked = slot <= max;
            inv.setItem(guiSlot, homeItem(player, slot, home, unlocked));
        }
        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void openDeleteConfirm(Player player, int slot) {
        HomesGuiHolder holder = new HomesGuiHolder(MenuType.DELETE_CONFIRM, slot);
        Inventory inv = Bukkit.createInventory(holder, 27, Text.c(module.guiRaw("delete-title", "%slot%", String.valueOf(slot))));
        holder.inventory = inv;
        fill(inv);
        inv.setItem(11, button(Material.RED_CANDLE, module.guiRaw("delete-cancel-name"), module.guiRawList("delete-cancel-lore")));
        inv.setItem(13, button(Material.RED_BED, module.guiRaw("delete-confirm-name", "%slot%", String.valueOf(slot)),
                module.guiRawList("delete-confirm-lore", "%slot%", String.valueOf(slot))));
        inv.setItem(15, button(Material.LIME_CANDLE, module.guiRaw("delete-yes-name"), module.guiRawList("delete-yes-lore")));
        TrackedInventories.track(inv, holder);
        player.openInventory(inv);
    }

    void handleClick(Player player, HomesGuiHolder holder, int clickedSlot, org.bukkit.event.inventory.ClickType click) {
        switch (holder.type) {
            case MAIN -> handleMainClick(player, clickedSlot, click);
            case DELETE_CONFIRM -> handleDeleteClick(player, holder.slot, clickedSlot);
        }
    }

    private void handleMainClick(Player player, int clickedSlot, org.bukkit.event.inventory.ClickType click) {
        Integer homeSlot = module.homeSlotForGuiSlot(clickedSlot);
        if (homeSlot == null) return;
        int max = module.maxHomes(player);
        if (homeSlot > max) {
            module.send(player, "slot-locked", "%slot%", String.valueOf(homeSlot), "%max%", String.valueOf(max));
            return;
        }
        HomesDatabase.Home home = module.database().getHome(player.getUniqueId(), homeSlot);
        if (home == null) {
            module.send(player, "empty-slot", "%slot%", String.valueOf(homeSlot));
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
            module.send(player, "deleted", "%slot%", String.valueOf(homeSlot));
            openMain(player);
        }
    }

    private ItemStack homeItem(Player player, int slot, HomesDatabase.Home home, boolean unlocked) {
        ConfigurationSection section = module.config().getConfigurationSection(home == null ? "gui.empty-item" : "gui.set-item");
        Material material = Material.matchMaterial(section == null ? (home == null ? "GRAY_DYE" : "RED_BED") :
                section.getString("material", home == null ? "GRAY_DYE" : "RED_BED"));
        if (material == null) material = home == null ? Material.GRAY_DYE : Material.RED_BED;

        String nameKey = home == null ? "empty-name" : "set-name";
        String loreKey = home == null ? "empty-lore" : "set-lore";
        String defaultName = home == null ? "&#AAAAAA&lHOME &#AAAAAA%slot%" : "&#FF005D&lHOME &#FF005D%slot%";
        String name = section == null ? defaultName : section.getString("name", defaultName);
        List<String> loreTemplate = section == null ? List.of() : section.getStringList("lore");

        String world = home == null ? "-" : home.world();
        String coords = home == null ? "-" : home.x() + ", " + home.y() + ", " + home.z();
        String status = unlocked ? module.raw("unlocked") : module.raw("locked");

        name = applyPlaceholders(name, slot, world, coords, status);
        List<String> lore = new ArrayList<>();
        if (loreTemplate.isEmpty()) {
            if (home == null) {
                lore.add("&7Use &f/sethome " + slot + " &7to set this home.");
            } else {
                lore.add("&7World: &f" + world);
                lore.add("&7Location: &f" + coords);
                lore.add("");
                lore.add(module.guiRaw("click-teleport"));
                lore.add(module.guiRaw("shift-delete"));
            }
        } else {
            for (String line : loreTemplate) {
                lore.add(applyPlaceholders(line, slot, world, coords, status));
            }
        }
        if (!unlocked) {
            lore.add("");
            lore.add(module.guiRaw("permission-required", "%slot%", String.valueOf(slot)));
        }
        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    private String applyPlaceholders(String input, int slot, String world, String coords, String status) {
        return Text.apply(input,
                "%slot%", String.valueOf(slot),
                "%world%", world,
                "%coords%", coords,
                "%x%", coords.contains(",") ? coords.split(",")[0].trim() : "-",
                "%status%", status);
    }

    private void fill(Inventory inv) {
        Material filler = Material.matchMaterial(module.config().getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE"));
        if (filler == null) filler = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack pane = new ItemBuilder(filler).name(module.config().getString("gui.filler.name", " ")).build();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        return new ItemBuilder(material).name(name).lore(lore).build();
    }

    static Location toLocation(HomesDatabase.Home home) {
        if (home == null) return null;
        World world = Bukkit.getWorld(home.world());
        if (world == null) {
            for (World loaded : Bukkit.getWorlds()) {
                if (loaded.getName().equalsIgnoreCase(home.world())) {
                    world = loaded;
                    break;
                }
            }
        }
        if (world == null) return null;
        return new Location(world, home.x(), home.y(), home.z(), home.yaw(), home.pitch());
    }
}

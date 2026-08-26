package com.sharded.core.modules.tpa;

import com.sharded.core.util.HeadUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class TpaGuiHandler {

    static final class TpaGuiHolder implements InventoryHolder {
        final UUID targetId;
        final TpaType type;
        Inventory inventory;

        TpaGuiHolder(UUID targetId, TpaType type) {
            this.targetId = targetId;
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final TpaModule module;

    TpaGuiHandler(TpaModule module) {
        this.module = module;
    }

    void openRequestGui(Player requester, Player target, TpaType type) {
        TpaGuiHolder holder = new TpaGuiHolder(target.getUniqueId(), type);
        int size = module.config().getInt("gui.size", 27);
        String title = module.guiRaw("request-title", "%player%", target.getName());
        Inventory inv = Bukkit.createInventory(holder, size, Text.c(title));
        holder.inventory = inv;
        fill(inv);

        int cancelSlot = module.config().getInt("gui.slots.cancel", 10);
        int worldSlot = module.config().getInt("gui.slots.world", 12);
        int headSlot = module.config().getInt("gui.slots.head", 13);
        int compassSlot = module.config().getInt("gui.slots.compass", 14);
        int sendSlot = module.config().getInt("gui.slots.send", 16);

        inv.setItem(cancelSlot, button(Material.RED_CANDLE,
                module.guiRaw("cancel-name"), module.guiRawList("cancel-lore")));
        inv.setItem(worldSlot, worldItem(target));
        inv.setItem(headSlot, headItem(target));
        inv.setItem(compassSlot, compassItem(requester, target));
        inv.setItem(sendSlot, button(Material.LIME_CANDLE,
                module.guiRaw("send-name"), module.guiRawList("send-lore", "%player%", target.getName())));

        TrackedInventories.track(inv, holder);
        requester.openInventory(inv);
    }

    void handleClick(Player player, TpaGuiHolder holder, int slot) {
        int cancelSlot = module.config().getInt("gui.slots.cancel", 10);
        int sendSlot = module.config().getInt("gui.slots.send", 16);
        if (slot == cancelSlot) {
            player.closeInventory();
            module.send(player, "request-cancelled");
            return;
        }
        if (slot == sendSlot) {
            player.closeInventory();
            module.sendRequest(player, holder.targetId, holder.type);
        }
    }

    private ItemStack worldItem(Player target) {
        Location loc = target.getLocation();
        World world = loc.getWorld();
        Material icon = Material.GRASS_BLOCK;
        if (world != null) {
            Block block = world.getHighestBlockAt(loc.getBlockX(), loc.getBlockZ());
            if (block != null && block.getType().isSolid()) icon = block.getType();
        }
        List<String> lore = new ArrayList<>(module.guiRawList("world-lore",
                "%world%", world == null ? "?" : world.getName(),
                "%x%", String.valueOf(loc.getBlockX()),
                "%y%", String.valueOf(loc.getBlockY()),
                "%z%", String.valueOf(loc.getBlockZ())));
        return new ItemBuilder(icon)
                .name(module.guiRaw("world-name", "%world%", world == null ? "?" : world.getName()))
                .lore(lore)
                .build();
    }

    private ItemStack headItem(Player target) {
        ItemStack head = HeadUtil.namedHead(target.getName());
        return new ItemBuilder(head)
                .name(module.guiRaw("head-name", "%player%", target.getName()))
                .lore(module.guiRawList("head-lore", "%player%", target.getName()))
                .build();
    }

    private ItemStack compassItem(Player requester, Player target) {
        Location from = requester.getLocation();
        Location to = target.getLocation();
        int distance = (int) from.distance(to);
        return new ItemBuilder(Material.COMPASS)
                .name(module.guiRaw("compass-name"))
                .lore(module.guiRawList("compass-lore",
                        "%distance%", String.valueOf(distance),
                        "%player%", target.getName()))
                .build();
    }

    private void fill(Inventory inv) {
        Material filler = Material.matchMaterial(module.config().getString("gui.filler.material", "BLACK_STAINED_GLASS_PANE"));
        if (filler == null) filler = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack pane = new ItemBuilder(filler).name(" ").build();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        return new ItemBuilder(material).name(name).lore(lore).build();
    }
}

package com.shardedcore.modules.commands.tpa;

import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.GuiUtil;
import com.shardedcore.util.HeadUtil;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        Inventory inv = Bukkit.createInventory(holder, size,
                ColorUtil.parse(module.guiRaw("request-title", "%player%", target.getName())));
        holder.inventory = inv;
        fill(inv);
        inv.setItem(module.config().getInt("gui.slots.cancel", 10),
                GuiUtil.item(Material.RED_CANDLE, module.guiRaw("cancel-name"), module.guiRawList("cancel-lore")));
        inv.setItem(module.config().getInt("gui.slots.world", 12), worldItem(target));
        inv.setItem(module.config().getInt("gui.slots.head", 13), headItem(target));
        inv.setItem(module.config().getInt("gui.slots.compass", 14), compassItem(requester, target));
        inv.setItem(module.config().getInt("gui.slots.send", 16),
                GuiUtil.item(Material.LIME_CANDLE, module.guiRaw("send-name"),
                        module.guiRawList("send-lore", "%player%", target.getName())));
        TrackedInventories.track(inv, holder);
        requester.openInventory(inv);
    }

    void handleClick(Player player, TpaGuiHolder holder, int slot) {
        if (slot == module.config().getInt("gui.slots.cancel", 10)) {
            player.closeInventory();
            module.sendMessage(player, "request-cancelled");
            return;
        }
        if (slot == module.config().getInt("gui.slots.send", 16)) {
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
            if (block.getType().isSolid()) icon = block.getType();
        }
        return GuiUtil.item(icon, module.guiRaw("world-name", "%world%", world == null ? "?" : world.getName()),
                module.guiRawList("world-lore", "%world%", world == null ? "?" : world.getName(),
                        "%x%", String.valueOf(loc.getBlockX()), "%y%", String.valueOf(loc.getBlockY()),
                        "%z%", String.valueOf(loc.getBlockZ())));
    }

    private ItemStack headItem(Player target) {
        ItemStack head = HeadUtil.playerHead(target.getName());
        ItemMeta meta = head.getItemMeta();
        if (meta != null) {
            meta.displayName(ColorUtil.parse(module.guiRaw("head-name", "%player%", target.getName())));
            List<net.kyori.adventure.text.Component> lore = module.guiRawList("head-lore", "%player%", target.getName())
                    .stream().map(ColorUtil::parse).toList();
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack compassItem(Player requester, Player target) {
        int distance = (int) requester.getLocation().distance(target.getLocation());
        return GuiUtil.item(Material.COMPASS, module.guiRaw("compass-name"),
                module.guiRawList("compass-lore", "%distance%", String.valueOf(distance), "%player%", target.getName()));
    }

    private void fill(Inventory inv) {
        ItemStack pane = GuiUtil.filler(module.config().getConfigurationSection("gui.filler"));
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }
}

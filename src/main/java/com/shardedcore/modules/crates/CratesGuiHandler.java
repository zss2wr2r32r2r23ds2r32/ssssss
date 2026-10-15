package com.shardedcore.modules.crates;

import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.ItemBuilder;
import com.shardedcore.util.TrackedInventories;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;

import java.util.List;

final class CratesGuiHandler {

    enum Type { LIST, PREVIEW, EDITOR, DELETE_CONFIRM, OPEN_CONFIRM }

    static final class Holder implements InventoryHolder {
        final Type type; final String crateId; Inventory inventory;
        Holder(Type type, String crateId) { this.type = type; this.crateId = crateId; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final CratesModule module;
    CratesGuiHandler(CratesModule module) { this.module = module; }

    void openList(Player player) { open(player, Type.LIST, null, "&8Crates", 27); }
    void openPreview(Player player, String id) { open(player, Type.PREVIEW, id, "&8Preview: "+id, 54); }
    void openEditor(Player player, String id) { open(player, Type.EDITOR, id, "&8Edit: "+id, 54); }
    void openDeleteConfirm(Player player, String id) { open(player, Type.DELETE_CONFIRM, id, "&cDelete "+id+"?", 27); }
    void openOpenConfirm(Player player, String id) { open(player, Type.OPEN_CONFIRM, id, "&8Open "+id+"?", 27); }

    void finishOpen(Player player, String crateId) {
        if (!module.storage().hasKey(player, crateId)) { module.sendMessage(player, "no-key", "crate", crateId); return; }
        module.storage().consumeKey(player, crateId);
        module.storage().grantReward(player, crateId);
        module.sendMessage(player, "opened", "crate", crateId);
        player.closeInventory();
    }

    private void open(Player player, Type type, String crateId, String title, int size) {
        Holder holder = new Holder(type, crateId);
        Inventory inv = Bukkit.createInventory(holder, size, ColorUtil.parse(title));
        holder.inventory = inv;
        TrackedInventories.track(inv, holder);
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < size; i++) inv.setItem(i, filler);
        if (type == Type.LIST) {
            inv.setItem(11, new ItemBuilder(Material.ENDER_CHEST).name("&dPreview").build());
            inv.setItem(13, new ItemBuilder(Material.CHEST).name("&eHub").build());
            inv.setItem(15, new ItemBuilder(Material.BOOK).name("&bInfo").build());
        }
        if (type == Type.EDITOR) inv.setItem(49, new ItemBuilder(Material.LIME_DYE).name("&aSave & Place").lore(List.of("&7Places crate at your location")).build());
        if (type == Type.DELETE_CONFIRM || type == Type.OPEN_CONFIRM) {
            inv.setItem(11, new ItemBuilder(Material.RED_STAINED_GLASS_PANE).name("&cCancel").build());
            inv.setItem(15, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE).name("&aConfirm").build());
        }
        player.openInventory(inv);
    }

    void handleClick(InventoryClickEvent event) {
        Holder holder = TrackedInventories.lookup(event.getView().getTopInventory(), Holder.class);
        if (holder == null) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        int slot = event.getSlot();
        switch (holder.type) {
            case DELETE_CONFIRM -> { if (slot==15 && holder.crateId!=null) { module.storage().delete(holder.crateId); module.sendMessage(player,"deleted","crate",holder.crateId); player.closeInventory(); } else if (slot==11) player.closeInventory(); }
            case OPEN_CONFIRM -> { if (slot==15 && holder.crateId!=null) finishOpen(player, holder.crateId); else if (slot==11) player.closeInventory(); }
            case EDITOR -> { if (slot==49 && holder.crateId!=null) { module.storage().place(holder.crateId, player.getLocation()); module.sendMessage(player,"placed","crate",holder.crateId); player.closeInventory(); } }
            default -> {}
        }
    }
}

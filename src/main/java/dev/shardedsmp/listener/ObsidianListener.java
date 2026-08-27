package dev.shardedsmp.listener;

import dev.shardedsmp.ShardedSMP;
import dev.shardedsmp.item.ObsidianItems;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ObsidianListener implements Listener {
    private final ShardedSMP plugin;

    public ObsidianListener(ShardedSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (ObsidianItems.isSpecial(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(dev.shardedsmp.util.ColorUtil.color("&cObsidian cannot be dropped."));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (ObsidianItems.isAnyObsidian(item)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(dev.shardedsmp.util.ColorUtil.color("&cYou cannot place obsidian."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!ObsidianItems.isSpecial(item.getItemStack())) {
            if (item.getItemStack().getType() == Material.OBSIDIAN) {
                event.setCancelled(true);
                item.remove();
            }
            return;
        }
        item.setUnlimitedLifetime(true);
        item.setPersistent(true);
        item.setInvulnerable(true);
        item.setCanMobPickup(false);
        plugin.glowManager().glowEntity(item);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDespawn(ItemDespawnEvent event) {
        if (ObsidianItems.isSpecial(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerge(ItemMergeEvent event) {
        if (ObsidianItems.isSpecial(event.getEntity().getItemStack())
                || ObsidianItems.isSpecial(event.getTarget().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (ObsidianItems.isSpecial(event.getItem().getItemStack())
                || event.getItem().getItemStack().getType() == Material.OBSIDIAN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (ObsidianItems.isAnyObsidian(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        ItemStack stack = event.getItem().getItemStack();
        if (stack.getType() == Material.OBSIDIAN && !ObsidianItems.isSpecial(stack)) {
            event.setCancelled(true);
            event.getItem().remove();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPickupSpecial(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            if (ObsidianItems.isSpecial(event.getItem().getItemStack())) {
                event.setCancelled(true);
            }
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        if (!ObsidianItems.isSpecial(stack)) {
            return;
        }
        plugin.game().markObsidianFound(ObsidianItems.pieceId(stack));
        plugin.glowManager().setPlayerGlowing(player, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        Inventory clicked = event.getClickedInventory();
        Inventory top = event.getView().getTopInventory();

        if (ObsidianItems.isAnyObsidian(current) && !ObsidianItems.isSpecial(current)) {
            event.setCurrentItem(null);
            event.setCancelled(true);
            return;
        }
        if (ObsidianItems.isAnyObsidian(cursor) && !ObsidianItems.isSpecial(cursor)) {
            event.getWhoClicked().setItemOnCursor(null);
            event.setCancelled(true);
            return;
        }

        if (isBundle(current) && ObsidianItems.isSpecial(cursor)
                || isBundle(cursor) && ObsidianItems.isSpecial(current)) {
            event.setCancelled(true);
            return;
        }

        if (ObsidianItems.isSpecial(current) && clicked != null && !isAllowedInventory(clicked, event.getSlot())) {
            event.setCancelled(true);
            return;
        }
        if (ObsidianItems.isSpecial(cursor) && clicked != null && !isAllowedInventory(clicked, event.getSlot())) {
            event.setCancelled(true);
            return;
        }
        if (event.isShiftClick() && ObsidianItems.isSpecial(current) && !isPlayerOnlyView(top)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick().isKeyboardClick() && ObsidianItems.isSpecial(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()))) {
            if (clicked != null && !isAllowedInventory(clicked, event.getSlot())) {
                event.setCancelled(true);
            }
        }
        plugin.listenerEnchant().cap(current);
        plugin.listenerEnchant().cap(cursor);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (!ObsidianItems.isSpecial(event.getOldCursor())) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize && !isPlayerOnlyView(top)) {
                event.setCancelled(true);
                return;
            }
            if (slot < topSize && isPlayerOnlyView(top) && slot < 9) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        plugin.game().tickGlowAndHearts();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onItemFrame(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame
                && ObsidianItems.isAnyObsidian(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (ObsidianItems.isAnyObsidian(event.getPlayerItem()) || ObsidianItems.isAnyObsidian(event.getArmorStandItem())) {
            event.setCancelled(true);
        }
    }

    private boolean isAllowedInventory(Inventory inventory, int slot) {
        if (inventory instanceof PlayerInventory) {
            return slot >= 0 && slot <= 40;
        }
        return inventory.getType() == InventoryType.CRAFTING && slot >= 9;
    }

    private boolean isPlayerOnlyView(Inventory top) {
        InventoryType type = top.getType();
        return type == InventoryType.CRAFTING || type == InventoryType.PLAYER || type == InventoryType.CREATIVE;
    }

    private boolean isBundle(ItemStack item) {
        return item != null && item.getType().name().contains("BUNDLE");
    }
}

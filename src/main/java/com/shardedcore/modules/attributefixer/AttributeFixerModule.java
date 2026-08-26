package com.shardedcore.modules.attributefixer;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collection;
import java.util.Map;

public final class AttributeFixerModule extends Module implements Listener {

    public AttributeFixerModule(ShardedCore plugin) {
        super(plugin, "attributefixer");
    }

    @Override
    public void enable() {
        registerListener(this);
        if (config.getBoolean("fix-on-enable", true)) {
            Bukkit.getOnlinePlayers().forEach(this::fixPlayer);
        }
    }

    @Override
    public void disable() {
        cleanup();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (config.getBoolean("fix-on-join", true)) {
            Bukkit.getScheduler().runTask(plugin, () -> fixPlayer(event.getPlayer()));
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!config.getBoolean("fix-on-click", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            fix(event.getCurrentItem());
            fix(event.getCursor());
            fixPlayer(player);
        });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!config.getBoolean("fix-on-click", true)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> fixPlayer(player));
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!config.getBoolean("fix-on-click", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            fix(event.getMainHandItem());
            fix(event.getOffHandItem());
            fixPlayer(event.getPlayer());
        });
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (!config.getBoolean("fix-on-click", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> fixPlayer(event.getPlayer()));
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!config.getBoolean("fix-on-pickup", true)) return;
        if (!(event.getEntity() instanceof Player)) return;
        fix(event.getItem().getItemStack());
    }

    private void fixPlayer(Player player) {
        for (ItemStack item : player.getInventory().getContents()) fix(item);
        for (ItemStack item : player.getInventory().getArmorContents()) fix(item);
        fix(player.getInventory().getItemInOffHand());
    }

    private void fix(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        boolean resetAll = config.getBoolean("reset-all-custom", false);
        if (fixComponents(item, resetAll)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers()) return;
        if (!swapped(item.getType(), meta) && !resetAll) return;
        meta.setAttributeModifiers(null);
        item.setItemMeta(meta);
    }

    private boolean fixComponents(ItemStack item, boolean resetAll) {
        try {
            if (!item.hasData(DataComponentTypes.ATTRIBUTE_MODIFIERS)) return false;
            ItemAttributeModifiers modifiers = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            if (modifiers == null) return false;
            if (!resetAll && !swapped(item.getType(), modifiers)) return true;
            item.resetData(DataComponentTypes.ATTRIBUTE_MODIFIERS);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean swapped(Material material, ItemAttributeModifiers modifiers) {
        EquipmentSlot expected = slotOf(material);
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers()) {
            EquipmentSlotGroup group = entry.getGroup();
            if (group == null || group == EquipmentSlotGroup.ANY) continue;
            if (expected == EquipmentSlot.HAND) {
                if (group == EquipmentSlotGroup.HEAD || group == EquipmentSlotGroup.CHEST
                        || group == EquipmentSlotGroup.LEGS || group == EquipmentSlotGroup.FEET
                        || group == EquipmentSlotGroup.ARMOR) return true;
            } else if (!matches(expected, group)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(EquipmentSlot expected, EquipmentSlotGroup group) {
        if (group == EquipmentSlotGroup.ANY || group == EquipmentSlotGroup.HAND) return true;
        return switch (expected) {
            case HEAD -> group == EquipmentSlotGroup.HEAD || group == EquipmentSlotGroup.ARMOR;
            case CHEST -> group == EquipmentSlotGroup.CHEST || group == EquipmentSlotGroup.ARMOR;
            case LEGS -> group == EquipmentSlotGroup.LEGS || group == EquipmentSlotGroup.ARMOR;
            case FEET -> group == EquipmentSlotGroup.FEET || group == EquipmentSlotGroup.ARMOR;
            default -> true;
        };
    }

    private boolean swapped(Material material, ItemMeta meta) {
        EquipmentSlot expected = slotOf(material);
        var modifiers = meta.getAttributeModifiers();
        if (modifiers == null || modifiers.isEmpty()) return false;
        for (Map.Entry<Attribute, Collection<AttributeModifier>> entry : modifiers.asMap().entrySet()) {
            for (AttributeModifier modifier : entry.getValue()) {
                EquipmentSlot slot = modifier.getSlot();
                if (slot == null) continue;
                if (expected == EquipmentSlot.HAND) {
                    if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
                            || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) return true;
                } else if (slot != expected && slot != EquipmentSlot.HAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private EquipmentSlot slotOf(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET")) return EquipmentSlot.HEAD;
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return EquipmentSlot.CHEST;
        if (name.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (name.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return EquipmentSlot.HAND;
    }
}

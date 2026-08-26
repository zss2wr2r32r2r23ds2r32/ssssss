package com.shardedcore.modules.attributefixer;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
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
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasAttributeModifiers()) return;
        if (!swapped(item.getType(), meta)) {
            if (!config.getBoolean("reset-all-custom", false)) return;
        }
        meta.setAttributeModifiers(null);
        item.setItemMeta(meta);
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

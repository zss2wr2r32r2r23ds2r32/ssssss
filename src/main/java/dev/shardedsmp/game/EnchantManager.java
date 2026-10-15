package dev.shardedsmp.game;

import dev.shardedsmp.GamePhase;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public final class EnchantManager {
    private EnchantManager() {
    }

    public static int protectionCap(GamePhase phase) {
        return switch (phase) {
            case PHASE_1 -> 2;
            case PHASE_2 -> 3;
            case PHASE_3, PHASE_4, PHASE_5 -> 4;
            default -> Integer.MAX_VALUE;
        };
    }

    public static int sharpnessCap(GamePhase phase) {
        return switch (phase) {
            case PHASE_1 -> 1;
            case PHASE_2 -> 4;
            case PHASE_3, PHASE_4, PHASE_5 -> 5;
            default -> Integer.MAX_VALUE;
        };
    }

    public static boolean capItem(ItemStack item, GamePhase phase) {
        if (item == null || item.getType().isAir() || phase == GamePhase.IDLE) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        boolean changed = capMap(meta.getEnchants(), meta, phase, false);
        if (meta instanceof EnchantmentStorageMeta storage) {
            changed |= capMap(storage.getStoredEnchants(), storage, phase, true);
        }
        if (changed) {
            item.setItemMeta(meta);
        }
        return changed;
    }

    private static boolean capMap(Map<Enchantment, Integer> enchants, ItemMeta meta, GamePhase phase, boolean stored) {
        boolean changed = false;
        int protCap = protectionCap(phase);
        int sharpCap = sharpnessCap(phase);
        Integer prot = enchants.get(Enchantment.PROTECTION);
        if (prot != null && prot > protCap) {
            setEnchant(meta, Enchantment.PROTECTION, protCap, stored);
            changed = true;
        }
        Integer sharp = enchants.get(Enchantment.SHARPNESS);
        if (sharp != null && sharp > sharpCap) {
            setEnchant(meta, Enchantment.SHARPNESS, sharpCap, stored);
            changed = true;
        }
        return changed;
    }

    private static void setEnchant(ItemMeta meta, Enchantment enchantment, int level, boolean stored) {
        if (stored && meta instanceof EnchantmentStorageMeta storage) {
            storage.removeStoredEnchant(enchantment);
            if (level > 0) {
                storage.addStoredEnchant(enchantment, level, true);
            }
            return;
        }
        meta.removeEnchant(enchantment);
        if (level > 0) {
            meta.addEnchant(enchantment, level, true);
        }
    }

    public static boolean isRestrictedTool(Material material) {
        String name = material.name();
        boolean tool = name.endsWith("_SWORD")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE");
        return tool && !name.startsWith("NETHERITE_");
    }

    public static boolean netheriteOnly(GamePhase phase) {
        return phase.number() >= 3;
    }
}

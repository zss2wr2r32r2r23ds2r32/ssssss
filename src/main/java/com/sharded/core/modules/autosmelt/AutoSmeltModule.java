package com.sharded.core.modules.autosmelt;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /autosmelt - applies the custom "Auto Smelt" enchant to the pickaxe you are
 * holding. Ores mined with it drop their smelted form automatically.
 */
public final class AutoSmeltModule extends Module implements CommandExecutor {

    private NamespacedKey enchantKey;
    private final Map<Material, Material> smeltMap = new HashMap<>();

    public AutoSmeltModule(ShardedCore plugin) {
        super(plugin, "autosmelt");
    }

    @Override
    protected void onEnable() {
        enchantKey = new NamespacedKey(plugin, "autosmelt");
        registerCommand("autosmelt", this);
        smeltMap.clear();
        // Drop type -> smelted result, from config ("raw_iron: iron_ingot").
        var section = config.getConfigurationSection("smelt-map");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material from = Material.getMaterial(key.toUpperCase(Locale.ROOT));
                Material to = Material.getMaterial(section.getString(key, "").toUpperCase(Locale.ROOT));
                if (from != null && to != null) {
                    smeltMap.put(from, to);
                } else {
                    plugin.getLogger().warning("[autosmelt] Invalid smelt-map entry: " + key);
                }
            }
        }
    }

    public boolean hasAutoSmelt(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(enchantKey, PersistentDataType.BYTE);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.autosmelt.use")) {
            send(player, "no-permission");
            return true;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !Tag.ITEMS_PICKAXES.isTagged(item.getType())) {
            send(player, "not-a-pickaxe");
            return true;
        }
        if (hasAutoSmelt(item)) {
            send(player, "already-enchanted");
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(enchantKey, PersistentDataType.BYTE, (byte) 1);
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(0, Text.c(config.getString("lore-line", "&7Auto Smelt I")));
        meta.lore(lore);
        item.setItemMeta(meta);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
        send(player, "applied");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!hasAutoSmelt(tool)) return;
        if (!Tag.ITEMS_PICKAXES.isTagged(tool.getType())) return;

        Block block = event.getBlock();
        Collection<ItemStack> drops = block.getDrops(tool, player);
        if (drops.isEmpty()) return;

        boolean smeltedAnything = false;
        List<ItemStack> finalDrops = new ArrayList<>();
        for (ItemStack drop : drops) {
            Material result = smeltMap.get(drop.getType());
            if (result != null) {
                finalDrops.add(new ItemStack(result, drop.getAmount()));
                smeltedAnything = true;
            } else {
                finalDrops.add(drop);
            }
        }
        if (!smeltedAnything) return;

        event.setDropItems(false);
        for (ItemStack drop : finalDrops) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
        }
        if (config.getBoolean("play-sound", false)) {
            player.playSound(block.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.2f, 1.8f);
        }
    }
}

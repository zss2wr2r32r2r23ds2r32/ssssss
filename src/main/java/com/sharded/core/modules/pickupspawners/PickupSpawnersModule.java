package com.sharded.core.modules.pickupspawners;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PickupSpawnersModule extends Module implements CommandExecutor, TabCompleter {

    private NamespacedKey mobKey;
    private final Map<UUID, Integer> paidPickups = new HashMap<>();

    public PickupSpawnersModule(ShardedCore plugin) {
        super(plugin, "pickupspawners");
    }

    @Override
    protected void onEnable() {
        mobKey = new NamespacedKey(plugin, "spawner_mob");
        registerCommand("spawners", this);
    }

    @Override
    protected void onDisable() {
        paidPickups.clear();
    }

    private long price() {
        return config.getLong("price", 100L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.spawners.use")) {
            send(player, "no-permission");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("pay")) {
            send(player, "usage");
            return true;
        }
        long cost = price();
        if (args.length >= 2) {
            try {
                cost = Long.parseLong(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        TokenService tokens = plugin.modules().tokens();
        if (config.getBoolean("use-tokens", true)) {
            if (tokens == null || !tokens.take(player.getUniqueId(), cost)) {
                send(player, "not-enough", "%price%", String.valueOf(cost));
                return true;
            }
        }
        paidPickups.merge(player.getUniqueId(), 1, Integer::sum);
        send(player, "paid", "%price%", String.valueOf(cost));
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return TabCompleteHelper.filter(args[0], "pay");
        }
        return java.util.List.of();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("sharded.spawners.pickup")) {
            event.setCancelled(true);
            MessageUtil.deliver(player, raw("no-permission"), resolveDelivery("no-permission"));
            return;
        }

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (config.getBoolean("require-silk-touch", true) && !tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;

        if (paidPickups.getOrDefault(player.getUniqueId(), 0) <= 0) {
            event.setCancelled(true);
            MessageUtil.deliver(player, raw("actionbar-hint", "%price%", String.valueOf(price())), resolveDelivery("actionbar-hint"));
            return;
        }

        paidPickups.put(player.getUniqueId(), paidPickups.get(player.getUniqueId()) - 1);
        event.setDropItems(false);

        EntityType type = EntityType.PIG;
        if (event.getBlock().getState() instanceof CreatureSpawner cs && cs.getSpawnedType() != null) {
            type = cs.getSpawnedType();
        }

        ItemStack spawner = new ItemStack(Material.SPAWNER);
        BlockStateMeta meta = (BlockStateMeta) spawner.getItemMeta();
        CreatureSpawner state = (CreatureSpawner) meta.getBlockState();
        state.setSpawnedType(type);
        meta.setBlockState(state);
        meta.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, type.name());
        spawner.setItemMeta(meta);

        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), spawner);
        send(player, "picked-up", "%mob%", type.name().replace('_', ' '));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;
        ItemStack item = event.getItemInHand();
        if (!(item.getItemMeta() instanceof BlockStateMeta meta)) return;
        if (meta.getPersistentDataContainer().has(mobKey, PersistentDataType.STRING)) {
            String typeName = meta.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
            try {
                EntityType type = EntityType.valueOf(typeName);
                Block block = event.getBlockPlaced();
                if (block.getState() instanceof CreatureSpawner cs) {
                    cs.setSpawnedType(type);
                    cs.update(true, false);
                }
            } catch (IllegalArgumentException ignored) {
            }
        } else if (meta.getBlockState() instanceof CreatureSpawner placed) {
            Block block = event.getBlockPlaced();
            if (block.getState() instanceof CreatureSpawner cs) {
                cs.setSpawnedType(placed.getSpawnedType());
                cs.update(true, false);
            }
        }
    }
}

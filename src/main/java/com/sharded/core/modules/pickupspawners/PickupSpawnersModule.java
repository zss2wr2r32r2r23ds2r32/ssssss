package com.sharded.core.modules.pickupspawners;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pick up spawners with silk touch after paying via /spawners pay.
 * Shows an action bar hint when mining without payment.
 */
public final class PickupSpawnersModule extends Module implements CommandExecutor {

    private final Map<UUID, Integer> paidPickups = new HashMap<>();

    public PickupSpawnersModule(ShardedCore plugin) {
        super(plugin, "pickupspawners");
    }

    @Override
    protected void onEnable() {
        registerCommand("spawners", this);
    }

    @Override
    protected void onDisable() {
        paidPickups.clear();
    }

    private long price() {
        return config.getLong("price", 100L);
    }

    private boolean useTokens() {
        return config.getBoolean("use-tokens", true);
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
        if (useTokens()) {
            TokenService tokens = plugin.modules().tokens();
            if (tokens == null || !tokens.take(player.getUniqueId(), cost)) {
                send(player, "not-enough", "%price%", String.valueOf(cost));
                return true;
            }
        }
        paidPickups.merge(player.getUniqueId(), 1, Integer::sum);
        send(player, "paid", "%price%", String.valueOf(cost));
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.SPAWNER) return;
        Player player = event.getPlayer();
        if (!player.hasPermission("sharded.spawners.pickup")) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            if (config.getBoolean("require-silk-touch", true)) return;
        }

        int paid = paidPickups.getOrDefault(player.getUniqueId(), 0);
        if (paid <= 0) {
            event.setCancelled(true);
            String msg = raw("actionbar-hint", "%price%", String.valueOf(price()));
            player.sendActionBar(Text.c(msg));
            return;
        }

        paidPickups.put(player.getUniqueId(), paid - 1);
        event.setDropItems(false);
        Block block = event.getBlock();
        ItemStack spawner = new ItemStack(Material.SPAWNER);
        if (block.getState() instanceof CreatureSpawner cs) {
            // Spawner type preserved via block state when placed back - item is generic spawner in vanilla
        }
        block.getWorld().dropItemNaturally(block.getLocation(), spawner);
        send(player, "picked-up");
    }
}

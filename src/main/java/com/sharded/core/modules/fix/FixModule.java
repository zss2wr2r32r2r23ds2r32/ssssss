package com.sharded.core.modules.fix;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * /fix - repairs the item in your main hand. 6 hour cooldown by default,
 * bypassable with sharded.fix.bypass. Cooldowns persist across restarts.
 */
public final class FixModule extends Module implements CommandExecutor {

    public FixModule(ShardedCore plugin) {
        super(plugin, "fix");
    }

    @Override
    protected void onEnable() {
        registerCommand("fix", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.fix.use")) {
            send(player, "no-permission");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || !(item.getItemMeta() instanceof Damageable damageable) || item.getType().getMaxDurability() <= 0) {
            send(player, "not-repairable");
            return true;
        }
        if (damageable.getDamage() <= 0) {
            send(player, "already-repaired");
            return true;
        }

        long cooldownSeconds = config.getLong("cooldown-seconds", 21600L); // 6 hours
        if (!player.hasPermission("sharded.fix.bypass")) {
            long nextUse = plugin.stateStore().getLong(player.getUniqueId(), "fix-next-use", 0L);
            long now = System.currentTimeMillis();
            if (nextUse > now) {
                send(player, "on-cooldown", "%time%", Text.time((nextUse - now) / 1000L));
                return true;
            }
            plugin.stateStore().setLong(player.getUniqueId(), "fix-next-use", now + cooldownSeconds * 1000L);
        }

        damageable.setDamage(0);
        item.setItemMeta(damageable);
        if (config.getBoolean("play-sound", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
        }
        send(player, "fixed", "%item%", Text.pretty(item.getType().getKey().getKey()));
        return true;
    }
}

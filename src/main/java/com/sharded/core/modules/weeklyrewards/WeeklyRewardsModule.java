package com.sharded.core.modules.weeklyrewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.RewardSpin;
import com.sharded.core.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/** Weekly reward wheel with action-bar spin animation. */
public final class WeeklyRewardsModule extends Module implements CommandExecutor {

    private static final String LAST_CLAIM = "weekly-reward-last";

    public WeeklyRewardsModule(ShardedCore plugin) {
        super(plugin, "weeklyrewards");
    }

    @Override
    protected void onEnable() {
        registerCommand("weeklyrewards", this);
        registerCommand("weekly", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.weeklyrewards.use")) {
            send(player, "no-permission");
            return true;
        }
        long cooldownMs = config.getLong("cooldown-hours", 168L) * 3_600_000L;
        long now = System.currentTimeMillis();
        long last = plugin.stateStore().getLong(player.getUniqueId(), LAST_CLAIM, 0L);
        if (last > 0 && now - last < cooldownMs) {
            long left = (cooldownMs - (now - last)) / 1000L;
            send(player, "cooldown", "%time%", Text.timeDaysHours(left));
            return true;
        }
        if (RewardSpin.isClaiming(player.getUniqueId())) {
            send(player, "already-claiming");
            return true;
        }
        List<RewardSpin.RewardOption> options = RewardSpin.loadOptions(config.getConfigurationSection("rewards"));
        if (options.isEmpty()) {
            send(player, "no-rewards");
            return true;
        }
        if (!RewardSpin.spin(this, plugin, player, options, LAST_CLAIM, "won", config)) {
            send(player, "already-claiming");
        }
        return true;
    }
}

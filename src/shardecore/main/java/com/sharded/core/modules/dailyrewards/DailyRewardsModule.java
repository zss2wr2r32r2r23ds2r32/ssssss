package com.sharded.core.modules.dailyrewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.RewardSpin;
import com.sharded.core.util.Text;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class DailyRewardsModule extends Module implements CommandExecutor {
   private static final String LAST_CLAIM = "daily-reward-last";

   public DailyRewardsModule(ShardedCore plugin) {
      super(plugin, "dailyrewards");
   }

   public long cooldownMillis() {
      return this.config.getLong("cooldown-hours", 24L) * 3600000L;
   }

   @Override
   protected void onEnable() {
      this.registerCommand("dailyrewards", this);
      this.registerCommand("daily", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.dailyrewards.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            long i = this.config.getLong("cooldown-hours", 24L) * 3600000L;
            long j = System.currentTimeMillis();
            long k = this.plugin.stateStore().getLong(player.getUniqueId(), "daily-reward-last", 0L);
            if (k > 0L && j - k < i) {
               long l = (i - (j - k)) / 1000L;
               this.send(player, "cooldown", new String[]{"%time%", Text.timeWeeksDaysMinutes(l)});
               return true;
            } else if (RewardSpin.isClaiming(player.getUniqueId())) {
               this.send(player, "already-claiming", new String[0]);
               return true;
            } else {
               List<RewardSpin.RewardOption> list = RewardSpin.loadOptions(this.config.getConfigurationSection("rewards"));
               if (list.isEmpty()) {
                  this.send(player, "no-rewards", new String[0]);
                  return true;
               } else {
                  if (!RewardSpin.spin(this, this.plugin, player, list, "daily-reward-last", "won", this.config)) {
                     this.send(player, "already-claiming", new String[0]);
                  }

                  return true;
               }
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }
}

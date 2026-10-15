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

public final class FixModule extends Module implements CommandExecutor {
   public FixModule(ShardedCore plugin) {
      super(plugin, "fix");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("fix", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.fix.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            ItemStack itemstack = player.getInventory().getItemInMainHand();
            if (itemstack.getType().isAir() || !(itemstack.getItemMeta() instanceof Damageable damageable) || itemstack.getType().getMaxDurability() <= 0) {
               this.send(player, "not-repairable", new String[0]);
               return true;
            } else if (damageable.getDamage() <= 0) {
               this.send(player, "already-repaired", new String[0]);
               return true;
            } else {
               long k = this.config.getLong("cooldown-seconds", 21600L);
               if (!player.hasPermission("sharded.fix.bypass")) {
                  long i = this.plugin.stateStore().getLong(player.getUniqueId(), "fix-next-use", 0L);
                  long j = System.currentTimeMillis();
                  if (i > j) {
                     this.send(player, "on-cooldown", new String[]{"%time%", Text.time((i - j) / 1000L)});
                     return true;
                  }

                  this.plugin.stateStore().setLong(player.getUniqueId(), "fix-next-use", j + k * 1000L);
               }

               damageable.setDamage(0);
               itemstack.setItemMeta(damageable);
               if (this.config.getBoolean("play-sound", true)) {
                  player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7F, 1.4F);
               }

               this.send(player, "fixed", new String[]{"%item%", Text.pretty(itemstack.getType().getKey().getKey())});
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }
}

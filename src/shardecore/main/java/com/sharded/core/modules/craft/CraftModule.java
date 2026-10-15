package com.sharded.core.modules.craft;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CraftModule extends Module implements CommandExecutor {
   public CraftModule(ShardedCore plugin) {
      super(plugin, "craft");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("craft", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.craft.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            player.openWorkbench(null, true);
            if (this.config.getBoolean("play-sound", true)) {
               player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.6F, 1.2F);
            }

            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }
}

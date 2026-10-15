package com.sharded.core.modules.kill;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class KillModule extends Module implements CommandExecutor, TabCompleter {
   public KillModule(ShardedCore plugin) {
      super(plugin, "kill");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("kill", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      Player player;
      if (args.length == 0) {
         if (!(sender instanceof Player player1)) {
            this.send(sender, "players-only", new String[0]);
            return true;
         }

         player = player1;
      } else {
         if (!sender.hasPermission("sharded.kill.others")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         }

         player = Bukkit.getPlayerExact(args[0]);
         if (player == null) {
            this.send(sender, "player-not-found", new String[]{"%player%", args[0]});
            return true;
         }
      }

      if (!sender.hasPermission("sharded.kill.use")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else {
         player.setHealth(0.0);
         if (sender != player) {
            this.send(sender, "killed-other", new String[]{"%player%", player.getName()});
         }

         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1 && sender.hasPermission("sharded.kill.others")) {
         List<String> list = new ArrayList<>();

         for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
               list.add(player.getName());
            }
         }

         return list;
      } else {
         return List.of();
      }
   }
}

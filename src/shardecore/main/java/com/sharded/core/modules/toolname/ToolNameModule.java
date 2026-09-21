package com.sharded.core.modules.toolname;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import com.sharded.core.util.WordBlacklist;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class ToolNameModule extends Module implements CommandExecutor {
   public ToolNameModule(ShardedCore plugin) {
      super(plugin, "toolname");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("toolname", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.toolname.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            ItemStack itemstack = player.getInventory().getItemInMainHand();
            if (itemstack.getType().isAir()) {
               this.send(player, "no-item", new String[0]);
               return true;
            } else if (args.length == 0) {
               ItemMeta itemmeta1 = itemstack.getItemMeta();
               if (itemmeta1 != null && itemmeta1.hasDisplayName()) {
                  itemmeta1.displayName(null);
                  itemstack.setItemMeta(itemmeta1);
               }

               this.send(player, "cleared", new String[0]);
               return true;
            } else {
               String s = String.join(" ", args);
               if (WordBlacklist.contains(this.config, "blacklist", s)) {
                  this.send(player, "blacklisted", new String[0]);
                  return true;
               } else {
                  ItemMeta itemmeta = itemstack.getItemMeta();
                  if (itemmeta == null) {
                     this.send(player, "failed", new String[0]);
                     return true;
                  } else {
                     itemmeta.displayName(Text.c(s));
                     itemstack.setItemMeta(itemmeta);
                     this.send(player, "renamed", new String[]{"%name%", s});
                     return true;
                  }
               }
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }
}

package com.sharded.core.modules.abilities;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.Numbers;
import java.io.File;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AbilitiesShopModule extends Module implements CommandExecutor {
   public AbilitiesShopModule(ShardedCore plugin) {
      super(plugin, "abilities");
   }

   @Override
   protected void onEnable() {
      this.syncJarResource("shop.yml");
      this.plugin.gui().loadMenu(new File(this.moduleFolder(), "shop.yml"), "abilities");
   }

   @Override
   protected void onDisable() {
   }

   public boolean tryPurchase(Player player, String permission, int days, long cost) {
      if (!player.hasPermission("sharded.tokenshop.use")) {
         this.send(player, "no-permission", new String[0]);
         return false;
      } else if (!this.plugin.luckPerms().isAvailable()) {
         this.send(player, "lp-missing", new String[0]);
         return false;
      } else if (permission != null && !permission.isBlank()) {
         TokenService tokenservice = this.plugin.modules().tokens();
         if (tokenservice == null) {
            return false;
         } else {
            long i = tokenservice.getBalance(player.getUniqueId());
            if (i < cost) {
               this.send(player, "not-enough-tokens", new String[]{"%missing%", Numbers.format(cost - i)});
               return false;
            } else if (!tokenservice.take(player.getUniqueId(), cost)) {
               this.send(player, "not-enough-tokens", new String[]{"%missing%", Numbers.format(cost - i)});
               return false;
            } else {
               String s = Math.max(1, days) + "d";
               this.plugin.luckPerms().runConsole("lp user " + player.getName() + " permission settemp " + permission + " true " + s);
               this.send(player, "purchased", new String[]{"%ability%", this.prettyPermission(permission), "%days%", String.valueOf(days)});
               player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
               return true;
            }
         }
      } else {
         return false;
      }
   }

   private String prettyPermission(String permission) {
      String s = permission;
      if (permission.startsWith("sharded.")) {
         s = permission.substring("sharded.".length());
      }

      s = s.replace('.', ' ');
      return s.isEmpty() ? permission : Character.toUpperCase(s.charAt(0)) + s.substring(1);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.tokenshop.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            this.plugin.gui().open(player, "abilities");
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }
}

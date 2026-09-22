package com.sharded.core.modules.staffpromote;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class StaffPromoteModule extends Module implements CommandExecutor, TabCompleter {
   public static final List<String> ROLES = List.of(
      "default", "helper", "sr-helper", "mod", "sr-mod", "admin", "sr-admin", "manager", "developer", "director", "owner"
   );
   private StaffPromoteDatabase database;

   public StaffPromoteModule(ShardedCore plugin) {
      super(plugin, "staffpromote");
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new StaffPromoteDatabase(this.moduleFolder(), this.config);
      } catch (SQLException sqlexception) {
         throw new IllegalStateException("Could not open promotions database", sqlexception);
      }

      this.registerCommand("promote", this);
      this.registerCommand("demote", this);
   }

   @Override
   protected void onDisable() {
      if (this.database != null) {
         this.database.close();
         this.database = null;
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().equalsIgnoreCase("demote") ? "demote" : "promote";
      String s1 = s.equals("promote") ? "sharded.promote.use" : "sharded.demote.use";
      if (!sender.hasPermission(s1) && !sender.hasPermission("sharded.promote.admin")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length != 2) {
         this.send(sender, s + "-usage", new String[0]);
         return true;
      } else {
         String s2 = this.canonicalPlayerName(args[0]);
         if (s2 == null) {
            this.send(sender, "invalid-player", new String[]{"%player%", args[0]});
            return true;
         } else {
            String s3 = args[1].toLowerCase(Locale.ROOT);
            if (!ROLES.contains(s3)) {
               this.send(sender, "invalid-role", new String[]{"%role%", args[1]});
               return true;
            } else {
               try {
                  this.database.log(sender.getName(), s2, s, s3);
               } catch (SQLException sqlexception) {
                  this.plugin.getLogger().severe("[staffpromote] Could not log " + s + " action: " + sqlexception.getMessage());
                  this.send(sender, "database-error", new String[0]);
                  return true;
               }

               boolean flag = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + s2 + " parent set " + s3);
               if (!flag) {
                  this.send(sender, "dispatch-failed", new String[0]);
                  return true;
               } else {
                  this.send(sender, s + "-success", new String[]{"%player%", s2, "%role%", s3});
                  return true;
               }
            }
         }
      }
   }

   private String canonicalPlayerName(String raw) {
      if (raw != null && raw.matches("[A-Za-z0-9_]{1,16}")) {
         Player player = Bukkit.getPlayerExact(raw);
         return player == null ? raw : player.getName();
      } else {
         return null;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().equalsIgnoreCase("demote") ? "sharded.demote.use" : "sharded.promote.use";
      if (!sender.hasPermission(s) && !sender.hasPermission("sharded.promote.admin")) {
         return List.of();
      } else if (args.length != 1) {
         return args.length == 2 ? TabCompleteHelper.filter(args[1], ROLES) : List.of();
      } else {
         List<String> list = new ArrayList<>();

         for (Player player : Bukkit.getOnlinePlayers()) {
            list.add(player.getName());
         }

         list.sort(String.CASE_INSENSITIVE_ORDER);
         return TabCompleteHelper.filter(args[0], list);
      }
   }
}

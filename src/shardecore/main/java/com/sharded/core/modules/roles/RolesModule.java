package com.sharded.core.modules.roles;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;

public final class RolesModule extends Module implements CommandExecutor, TabCompleter {
   public RolesModule(ShardedCore plugin) {
      super(plugin, "roles");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("role", this);
      this.registerCommand("roles", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("sharded.roles.admin")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length < 1) {
         this.send(sender, "usage", new String[0]);
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         ConfigurationSection configurationsection = this.config.getConfigurationSection("roles." + s);
         if (configurationsection == null) {
            this.send(sender, "unknown-role", new String[]{"%role%", s});
            return true;
         } else if (!this.plugin.luckPerms().isAvailable()) {
            this.send(sender, "lp-missing", new String[0]);
            return true;
         } else {
            Set<String> set = this.collectPermissions(s, configurationsection);
            String s1 = configurationsection.getString("luckperms-group", s);

            for (String s2 : set) {
               if (s2 != null && !s2.isBlank()) {
                  this.plugin.luckPerms().runConsole("lp group " + s1 + " permission set " + s2 + " true");
               }
            }

            this.send(sender, "applied", new String[]{"%role%", s, "%group%", s1, "%count%", String.valueOf(set.size())});
            return true;
         }
      }
   }

   private Set<String> collectPermissions(String roleId, ConfigurationSection role) {
      Set<String> set = new LinkedHashSet<>();
      if (role.getBoolean("grant-all", false)) {
         ConfigurationSection configurationsection = this.config.getConfigurationSection("roles");
         if (configurationsection != null) {
            for (String s : configurationsection.getKeys(false)) {
               ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
               if (configurationsection1 != null) {
                  set.addAll(configurationsection1.getStringList("permissions"));
               }
            }
         }

         set.addAll(this.config.getStringList("staff-permissions"));
         ConfigurationSection configurationsection2 = this.config.getConfigurationSection("all-permissions");
         if (configurationsection2 != null) {
            for (String s3 : configurationsection2.getKeys(false)) {
               String s1 = this.config.getString("all-permissions." + s3);
               if (s1 != null && !s1.isBlank()) {
                  set.add(s1);
               }
            }
         }
      } else {
         set.addAll(role.getStringList("permissions"));
         if (role.getBoolean("include-staff", false)) {
            set.addAll(this.config.getStringList("staff-permissions"));
         }

         for (String s2 : role.getStringList("inherit-permissions")) {
            ConfigurationSection configurationsection3 = this.config.getConfigurationSection("roles." + s2);
            if (configurationsection3 != null) {
               set.addAll(configurationsection3.getStringList("permissions"));
            }
         }
      }

      return set;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.roles.admin")) {
         return List.of();
      } else {
         ConfigurationSection configurationsection = this.config.getConfigurationSection("roles");
         if (configurationsection == null) {
            return List.of();
         } else {
            return args.length == 1 ? TabCompleteHelper.filter(args[0], new ArrayList<>(configurationsection.getKeys(false))) : List.of();
         }
      }
   }
}

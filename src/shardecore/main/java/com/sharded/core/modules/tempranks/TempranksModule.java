package com.sharded.core.modules.tempranks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.Numbers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public final class TempranksModule extends Module implements CommandExecutor, TabCompleter {
   public TempranksModule(ShardedCore plugin) {
      super(plugin, "tempranks");
   }

   @Override
   protected void onEnable() {
      this.rewriteBuffedShop();
      File file1 = this.syncJarResource("tempranks.yml");
      this.plugin.gui().loadMenu(file1, "tempranks");
      this.registerCommand("temprank", this);
      this.registerCommand("rankshop", this);
      this.registerCommand("temprankshop", this);
   }

   private void rewriteBuffedShop() {
      File file1 = new File(this.moduleFolder(), "tempranks.yml");
      YamlConfiguration yamlconfiguration = file1.isFile() ? YamlConfiguration.loadConfiguration(file1) : new YamlConfiguration();
      if (yamlconfiguration.getInt("config-version", 0) < 5) {
         InputStream inputstream = this.plugin.getResource("modules/perks/tempranks/tempranks.yml");
         InputStream inputstream1 = inputstream != null ? inputstream : this.plugin.getResource("modules/tempranks/tempranks.yml");
         if (inputstream1 != null) {
            try {
               InputStream inputstream2 = inputstream1;

               try {
                  if (file1.getParentFile() != null) {
                     file1.getParentFile().mkdirs();
                  }

                  Files.copy(inputstream1, file1.toPath(), StandardCopyOption.REPLACE_EXISTING);
               } catch (Throwable throwable1) {
                  if (inputstream1 != null) {
                     try {
                        inputstream2.close();
                     } catch (Throwable throwable) {
                        throwable1.addSuppressed(throwable);
                     }
                  }

                  throw throwable1;
               }

               if (inputstream1 != null) {
                  inputstream1.close();
               }
            } catch (Exception exception) {
            }
         }
      }
   }

   @Override
   protected void onDisable() {
   }

   public boolean tryPurchase(Player player, String rank, int days, long cost) {
      if (!player.hasPermission("sharded.tempranks.use")) {
         this.send(player, "no-permission", new String[0]);
         return false;
      } else if (!this.plugin.luckPerms().isAvailable()) {
         this.send(player, "lp-missing", new String[0]);
         return false;
      } else {
         List<String> list = this.config.getStringList("rank-order");
         String s = rank.toLowerCase(Locale.ROOT);
         int i = this.plugin.luckPerms().rankIndex(list, s);
         if (this.plugin.luckPerms().hasPermanentGroup(player.getUniqueId(), s)) {
            this.send(player, "already-has-permanent", new String[]{"%rank%", this.prettyRank(s)});
            return false;
         } else {
            Optional<String> optional = this.plugin.luckPerms().highestPermanentRank(player.getUniqueId(), list);
            if (optional.isPresent()) {
               int j = this.plugin.luckPerms().rankIndex(list, optional.get());
               if (j >= i && i >= 0) {
                  this.send(player, "has-higher-rank", new String[]{"%rank%", this.prettyRank(optional.get())});
                  return false;
               }
            }

            if (this.plugin.luckPerms().hasActiveTempGroup(player.getUniqueId(), s)) {
               Optional<Duration> optional1 = this.plugin.luckPerms().tempGroupTimeLeft(player.getUniqueId(), s);
               String s1 = optional1.<String>map(d -> Text.time(Math.max(1L, d.getSeconds()))).orElse("?");
               this.send(player, "already-has-temp", new String[]{"%rank%", this.prettyRank(s), "%time%", s1});
               return false;
            } else {
               TokenService tokenservice = this.plugin.modules().tokens();
               if (tokenservice == null) {
                  return false;
               } else {
                  long k = tokenservice.getBalance(player.getUniqueId());
                  if (k < cost) {
                     this.send(player, "not-enough-tokens", new String[]{"%missing%", Numbers.format(cost - k)});
                     return false;
                  } else if (!tokenservice.take(player.getUniqueId(), cost)) {
                     this.send(player, "not-enough-tokens", new String[]{"%missing%", Numbers.format(cost - k)});
                     return false;
                  } else {
                     this.plugin.luckPerms().runConsole("lp user " + player.getName() + " parent addtemp " + s + " " + days + "d");
                     this.send(player, "purchased", new String[]{"%rank%", this.prettyRank(s), "%days%", String.valueOf(days)});
                     return true;
                  }
               }
            }
         }
      }
   }

   private String prettyRank(String rankId) {
      return rankId != null && !rankId.isBlank() ? Character.toUpperCase(rankId.charAt(0)) + rankId.substring(1).toLowerCase(Locale.ROOT) : rankId;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.tempranks.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else if (args.length != 0 && !args[0].equalsIgnoreCase("shop")) {
            this.send(player, "usage", new String[0]);
            return true;
         } else {
            this.plugin.gui().open(player, this.config.getString("main-menu", "tempranks"));
            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return args.length == 1 ? TabCompleteHelper.filter(args[0], "shop") : List.of();
   }
}

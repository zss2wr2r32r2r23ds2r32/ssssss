package com.sharded.core.modules.announce;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.Text;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class AnnounceModule extends Module implements CommandExecutor, TabCompleter {
   public AnnounceModule(ShardedCore plugin) {
      super(plugin, "announce");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("announce", this);
      this.registerCommand("announcement", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("sharded.announce.use") && !sender.hasPermission("sharded.staff.announce")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(sender, "usage", new String[0]);
         return true;
      } else {
         String s = String.join(" ", args);
         this.broadcastAnnouncement(s);
         this.send(sender, "sent", new String[0]);
         return true;
      }
   }

   private void broadcastAnnouncement(String message) {
      String s = this.config.getString("display-mode", "title").toLowerCase(Locale.ROOT);
      boolean flag = s.equals("title") || s.equals("both");
      boolean flag1 = s.equals("chat") || s.equals("both");
      if (flag) {
         String s1 = this.config.getString("title-text", "&#00A2FF&lANNOUNCEMENT");
         int i = this.config.getInt("title-fade-in", 10);
         int j = this.config.getInt("title-stay", 70);
         int k = this.config.getInt("title-fade-out", 20);
         Times times = Times.times(Duration.ofMillis((long)i * 50L), Duration.ofMillis((long)j * 50L), Duration.ofMillis((long)k * 50L));
         Title title = Title.title(Text.c(s1), Text.c(message), times);

         for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
         }
      }

      if (flag1) {
         String s2 = this.raw("format", new String[]{"%message%", message});
         MessageUtil.Delivery messageutil$delivery = this.resolveDelivery("broadcast");
         Component component = Text.c(s2);

         for (Player player1 : Bukkit.getOnlinePlayers()) {
            MessageUtil.deliver(player1, component, messageutil$delivery);
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return List.of();
   }
}

package com.sharded.core.modules.requeststaff;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class RequestStaffModule extends Module implements CommandExecutor {
   private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
   private final Map<UUID, UUID> claimedBy = new ConcurrentHashMap<>();

   public RequestStaffModule(ShardedCore plugin) {
      super(plugin, "requeststaff");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("requeststaff", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length >= 2 && args[0].equalsIgnoreCase("tp")) {
         if (sender instanceof Player player2) {
            this.handleStaffTeleport(player2, args[1]);
            return true;
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      } else if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.requeststaff.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else {
            long i = this.config.getLong("cooldown-seconds", 30L) * 1000L;
            long j = System.currentTimeMillis();
            Long olong = this.cooldowns.get(player.getUniqueId());
            if (olong != null && j - olong < i) {
               this.send(player, "cooldown", new String[]{"%time%", String.valueOf((i - (j - olong)) / 1000L + 1L)});
               return true;
            } else {
               this.cooldowns.put(player.getUniqueId(), j);
               this.claimedBy.remove(player.getUniqueId());
               String s = this.config.getString("staff-permission", "sharded.staff");
               Component component = this.buildStaffAlert(player.getName(), s);

               for (Player player1 : Bukkit.getOnlinePlayers()) {
                  if (player1.hasPermission(s)) {
                     player1.sendMessage(component);
                  }
               }

               this.send(player, "sent", new String[0]);
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private Component buildStaffAlert(String playerName, String staffPerm) {
      String s = this.raw("staff-alert", new String[]{"%player%", playerName});
      String s1 = this.raw("staff-alert-spacer", new String[0]);
      String s2 = this.raw("click-here", new String[0]);
      Component component = Text.c(s2)
         .clickEvent(ClickEvent.runCommand("/requeststaff tp " + playerName))
         .hoverEvent(HoverEvent.showText(Text.c(this.raw("click-hover", new String[0]))));
      return Component.join(JoinConfiguration.newlines(), new ComponentLike[]{Text.c(s), Text.c(s1.isEmpty() ? " " : s1), component});
   }

   public boolean handleStaffTeleport(Player staff, String targetName) {
      String s = this.config.getString("staff-permission", "sharded.staff");
      String s1 = this.config.getString("respond-permission", "sharded.staff.requeststaff");
      if (!staff.hasPermission(s) && !staff.hasPermission(s1)) {
         return false;
      } else {
         Player player = Bukkit.getPlayerExact(targetName);
         if (player == null) {
            this.send(staff, "target-offline", new String[0]);
            return true;
         } else {
            UUID uuid = this.claimedBy.get(player.getUniqueId());
            if (uuid != null && !uuid.equals(staff.getUniqueId())) {
               this.send(staff, "already-helping", new String[0]);
               return true;
            } else {
               this.claimedBy.put(player.getUniqueId(), staff.getUniqueId());
               staff.teleport(player.getLocation());
               this.send(staff, "teleported", new String[]{"%player%", player.getName()});
               this.send(player, "staff-coming", new String[]{"%staff%", staff.getName()});
               return true;
            }
         }
      }
   }
}

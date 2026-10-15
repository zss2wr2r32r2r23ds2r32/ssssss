package com.sharded.core.modules.privatemessages;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.CommandOverride;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PrivateMessagesModule extends Module implements CommandExecutor, TabCompleter {
   public static final String STATE_KEY = "msg-enabled";
   private final Map<UUID, UUID> lastConversation = new HashMap<>();

   public PrivateMessagesModule(ShardedCore plugin) {
      super(plugin, "privatemessages");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("msg", this);
      this.registerCommand("reply", this);
      this.registerCommand("msgtoggle", this);
      CommandOverride.takeOver(this.plugin, "msgtoggle", this, this);
      CommandOverride.takeOver(this.plugin, "togglemsg", this, this);
      CommandOverride.takeOver(this.plugin, "pmtoggle", this, this);
   }

   @Override
   protected void onDisable() {
      this.lastConversation.clear();
   }

   public boolean isMsgEnabled(Player player) {
      return this.plugin.stateStore().getBool(player.getUniqueId(), "msg-enabled", true);
   }

   public void setMsgEnabled(Player player, boolean enabled) {
      this.plugin.stateStore().setBool(player.getUniqueId(), "msg-enabled", enabled);
      this.send(player, enabled ? "msg-enabled" : "msg-disabled", new String[0]);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         String s = command.getName().toLowerCase();
         switch (s) {
            case "msgtoggle":
            case "togglemsg":
            case "pmtoggle":
               if (!player.hasPermission("sharded.msg.toggle")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               this.setMsgEnabled(player, !this.isMsgEnabled(player));
               break;
            case "msg":
               if (!player.hasPermission("sharded.msg.use")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               if (args.length < 2) {
                  this.send(player, "msg-usage", new String[0]);
                  return true;
               }

               Player player2 = Bukkit.getPlayerExact(args[0]);
               if (player2 == null || !player2.isOnline()) {
                  this.send(player, "player-not-found", new String[]{"%player%", args[0]});
                  return true;
               }

               this.deliver(player, player2, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
               break;
            case "reply":
               if (!player.hasPermission("sharded.msg.use")) {
                  this.send(player, "no-permission", new String[0]);
                  return true;
               }

               if (args.length < 1) {
                  this.send(player, "reply-usage", new String[0]);
                  return true;
               }

               UUID uuid = this.lastConversation.get(player.getUniqueId());
               Player player1 = uuid == null ? null : Bukkit.getPlayer(uuid);
               if (player1 == null || !player1.isOnline()) {
                  this.send(player, "nobody-to-reply", new String[0]);
                  return true;
               }

               this.deliver(player, player1, String.join(" ", args));
         }

         return true;
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void deliver(Player from, Player to, String message) {
      if (from.equals(to)) {
         this.send(from, "cannot-message-self", new String[0]);
      } else if (!this.isMsgEnabled(to) && !from.hasPermission("sharded.msg.bypass")) {
         this.send(from, "target-has-msg-disabled", new String[]{"%player%", to.getName()});
      } else {
         this.send(from, "format-sent", new String[]{"%player%", to.getName(), "%message%", message});
         this.send(to, "format-received", new String[]{"%player%", from.getName(), "%message%", message});
         if (this.config.getBoolean("play-sound", true)) {
            to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.6F);
         }

         this.lastConversation.put(from.getUniqueId(), to.getUniqueId());
         this.lastConversation.put(to.getUniqueId(), from.getUniqueId());
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lastConversation.remove(event.getPlayer().getUniqueId());
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (command.getName().equalsIgnoreCase("msg") && args.length == 1) {
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

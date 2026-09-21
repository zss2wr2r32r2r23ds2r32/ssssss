package com.sharded.core.modules.staffchat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.staff.StaffModule;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StaffChatModule extends Module implements CommandExecutor {
   private final Set<UUID> toggleMode = ConcurrentHashMap.newKeySet();

   public StaffChatModule(ShardedCore plugin) {
      super(plugin, "staffchat");
   }

   public boolean isStaffChatMode(UUID uuid) {
      return this.toggleMode.contains(uuid);
   }

   public void setEnabled(Player player, boolean enabled, boolean notify) {
      if (enabled) {
         this.toggleMode.add(player.getUniqueId());
      } else {
         this.toggleMode.remove(player.getUniqueId());
      }

      if (notify) {
         this.send(player, enabled ? "enabled" : "disabled", new String[0]);
      }
   }

   public boolean isLockedByStaffMode(Player player) {
      StaffModule staffmodule = this.plugin.modules().get(StaffModule.class);
      return staffmodule != null && staffmodule.staffMode() != null && staffmodule.staffMode().isStaffMode(player.getUniqueId());
   }

   @Override
   protected void onEnable() {
      this.registerListener(this);
      this.registerCommand("staffchat", this);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = this.config.getString("permission", "sharded.staffchat.use");
      if (!sender.hasPermission(s)) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (sender instanceof Player player) {
         if (args.length > 0) {
            this.broadcast(player.getName(), String.join(" ", args));
            return true;
         } else {
            if (this.toggleMode.contains(player.getUniqueId())) {
               if (this.isLockedByStaffMode(player)) {
                  this.send(player, "staffmode-locked", new String[0]);
                  return true;
               }

               this.setEnabled(player, false, true);
            } else {
               this.setEnabled(player, true, true);
            }

            return true;
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      Player player = event.getPlayer();
      if (this.toggleMode.contains(player.getUniqueId())) {
         String s = this.config.getString("permission", "sharded.staffchat.use");
         if (!player.hasPermission(s)) {
            this.toggleMode.remove(player.getUniqueId());
         } else {
            event.setCancelled(true);
            String s1 = PlainTextComponentSerializer.plainText().serialize(event.message());
            if (!s1.isBlank()) {
               this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.broadcast(player.getName(), s1));
            }
         }
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.toggleMode.remove(event.getPlayer().getUniqueId());
   }

   private void broadcast(String playerName, String message) {
      String s = this.config.getString("permission", "sharded.staffchat.use");
      String s1 = this.config.getString("format", this.messages.getString("format", "&#AD4EFF&lSTAFF &8▷ &f%player%&7: &f%message%"));
      String s2 = Text.apply(s1.replace("%prefix%", this.messagePrefix()), "%player%", playerName, "%message%", message);
      Component component = Text.c(s2);

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (player.hasPermission(s)) {
            player.sendMessage(component);
         }
      }
   }
}

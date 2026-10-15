package com.sharded.core.modules.joinmessages;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.PlayerToggles;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinMessagesModule extends Module {
   public JoinMessagesModule(ShardedCore plugin) {
      super(plugin, "joinmessages");
   }

   @Override
   protected void onEnable() {
   }

   private String resolve(Player player, String type) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("formats");
      if (configurationsection == null) {
         return null;
      } else {
         ConfigurationSection configurationsection1 = null;
         int i = Integer.MIN_VALUE;

         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection2 = configurationsection.getConfigurationSection(s);
            if (configurationsection2 != null) {
               String s1 = configurationsection2.getString("permission", "");
               if (s1.isEmpty() || player.hasPermission(s1)) {
                  int j = configurationsection2.getInt("priority", 0);
                  if (j > i) {
                     i = j;
                     configurationsection1 = configurationsection2;
                  }
               }
            }
         }

         if (configurationsection1 == null) {
            return null;
         } else {
            String s2 = configurationsection1.getString(type, "");
            return s2 != null && !s2.isEmpty()
               ? Text.apply(
                  s2, "%player%", player.getName(), "%rank%", this.plugin.luckPerms().prefix(player), "%group%", this.plugin.luckPerms().primaryGroup(player)
               )
               : null;
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      String s = player.hasPlayedBefore() ? "join" : "first-join";
      String s1 = this.resolve(player, s);
      if (s1 == null && s.equals("first-join")) {
         s1 = this.resolve(player, "join");
      }

      if (s1 != null) {
         event.joinMessage(null);
         Component component = Text.c(s1);

         for (Player player1 : Bukkit.getOnlinePlayers()) {
            if (PlayerToggles.joinMessages(player1)) {
               player1.sendMessage(component);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onQuit(PlayerQuitEvent event) {
      String s = this.resolve(event.getPlayer(), "quit");
      if (s != null) {
         event.quitMessage(null);
         Component component = Text.c(s);

         for (Player player : Bukkit.getOnlinePlayers()) {
            if (PlayerToggles.joinMessages(player)) {
               player.sendMessage(component);
            }
         }
      }
   }
}

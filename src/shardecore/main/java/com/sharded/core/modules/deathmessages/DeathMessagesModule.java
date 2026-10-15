package com.sharded.core.modules.deathmessages;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.PlayerToggles;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class DeathMessagesModule extends Module {
   public DeathMessagesModule(ShardedCore plugin) {
      super(plugin, "deathmessages");
   }

   @Override
   protected void onEnable() {
   }

   @EventHandler(
      priority = EventPriority.HIGH
   )
   public void onDeath(PlayerDeathEvent event) {
      Player player = event.getPlayer();
      ConfigurationSection configurationsection = this.config.getConfigurationSection("formats");
      if (configurationsection != null) {
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

         if (configurationsection1 != null) {
            Player player1 = player.getKiller();
            String s2;
            if (player1 != null && !player1.equals(player)) {
               s2 = configurationsection1.getString("killed-by-player", configurationsection1.getString("death", ""));
            } else {
               s2 = configurationsection1.getString("death", "");
            }

            if (s2 != null && !s2.isEmpty()) {
               s2 = Text.apply(
                  s2,
                  "%player%",
                  player.getName(),
                  "%rank%",
                  this.plugin.luckPerms().prefix(player),
                  "%group%",
                  this.plugin.luckPerms().primaryGroup(player),
                  "%killer%",
                  player1 == null ? "" : player1.getName(),
                  "%killer_rank%",
                  player1 == null ? "" : this.plugin.luckPerms().prefix(player1)
               );
               event.deathMessage(null);
               Component component = Text.c(s2);

               for (Player player2 : this.plugin.getServer().getOnlinePlayers()) {
                  if (PlayerToggles.deathMessages(player2)) {
                     player2.sendMessage(component);
                  }
               }
            }
         }
      }
   }
}

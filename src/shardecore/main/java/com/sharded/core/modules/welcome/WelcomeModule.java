package com.sharded.core.modules.welcome;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class WelcomeModule extends Module implements CommandExecutor, TabCompleter {
   private static final String STATE_KEY = "welcome-eligible";
   private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Set<UUID>> welcomedBy = new ConcurrentHashMap<>();

   public WelcomeModule(ShardedCore plugin) {
      super(plugin, "welcome");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("w", this);

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (this.isNewPlayer(player)) {
            this.markEligible(player);
         }
      }
   }

   @Override
   protected void onDisable() {
      this.pending.clear();
      this.welcomedBy.clear();
   }

   @EventHandler(
      priority = EventPriority.MONITOR,
      ignoreCancelled = true
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline()) {
            if (this.isNewPlayer(player)) {
               this.markEligible(player);
            }
         }
      }, 5L);
   }

   private boolean isNewPlayer(Player player) {
      if (!player.hasPlayedBefore()) {
         return true;
      } else if (this.plugin.stateStore() != null && this.plugin.stateStore().getBool(player.getUniqueId(), "welcome-eligible", false)) {
         return true;
      } else {
         long i = Math.max(1L, this.config.getLong("new-player-minutes", 5L));

         try {
            long j = (long)player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            return j < i * 60L * 20L;
         } catch (IllegalArgumentException illegalargumentexception) {
            return false;
         }
      }
   }

   private void markEligible(Player player) {
      this.pending.add(player.getUniqueId());
      if (this.plugin.stateStore() != null) {
         this.plugin.stateStore().setBool(player.getUniqueId(), "welcome-eligible", true);
      }

      String s = this.config.getString("messages.join-announce");
      if (s != null && !s.isBlank()) {
         Bukkit.broadcast(Text.c(Text.apply(s, "%player%", player.getName())));
      }

      long i = Math.max(1L, this.config.getLong("expire-minutes", 30L));
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         this.pending.remove(player.getUniqueId());
         this.welcomedBy.remove(player.getUniqueId());
         if (this.plugin.stateStore() != null) {
            this.plugin.stateStore().setBool(player.getUniqueId(), "welcome-eligible", false);
         }
      }, i * 60L * 20L);
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID uuid = event.getPlayer().getUniqueId();
      this.pending.remove(uuid);
      this.welcomedBy.remove(uuid);

      for (Set<UUID> set : this.welcomedBy.values()) {
         set.remove(uuid);
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!(sender instanceof Player player)) {
         this.msg(sender, "players-only");
         return true;
      } else if (!player.hasPermission("sharded.welcome.use")) {
         this.msg(player, "no-permission");
         return true;
      } else if (args.length == 0) {
         if (this.pendingNames(player).isEmpty()) {
            this.msg(player, "none-pending");
         } else {
            this.msg(player, "usage");
         }

         return true;
      } else {
         Player player1 = Bukkit.getPlayerExact(args[0]);
         if (player1 != null && player1.isOnline()) {
            if (player1.getUniqueId().equals(player.getUniqueId())) {
               this.msg(player, "self");
               return true;
            } else if (!this.pending.contains(player1.getUniqueId()) && !this.isNewPlayer(player1)) {
               this.msg(player, "not-new", "%player%", player1.getName());
               return true;
            } else {
               this.pending.add(player1.getUniqueId());
               Set<UUID> set = this.welcomedBy.computeIfAbsent(player1.getUniqueId(), key -> ConcurrentHashMap.newKeySet());
               if (!set.add(player.getUniqueId())) {
                  this.msg(player, "already", "%player%", player1.getName());
                  return true;
               } else {
                  String s = this.config.getString("messages.broadcast", this.raw("broadcast", new String[0]));
                  if (s != null && !s.isBlank() && !s.equals("broadcast")) {
                     String s1 = Text.apply(s, "%welcomer%", player.getName(), "%player%", player1.getName());
                     Bukkit.broadcast(Text.c(s1));
                  } else {
                     this.msg(player, "success", "%player%", player1.getName());
                  }

                  return true;
               }
            }
         } else {
            this.msg(player, "offline", "%player%", args[0]);
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (args.length == 1 && sender instanceof Player player) {
         return !player.hasPermission("sharded.welcome.use") ? List.of() : TabCompleteHelper.filter(args[0], this.pendingNames(player));
      } else {
         return List.of();
      }
   }

   private List<String> pendingNames(Player viewer) {
      List<String> list = new ArrayList<>();

      for (Player player : Bukkit.getOnlinePlayers()) {
         if (!player.getUniqueId().equals(viewer.getUniqueId()) && (this.pending.contains(player.getUniqueId()) || this.isNewPlayer(player))) {
            Set<UUID> set = this.welcomedBy.get(player.getUniqueId());
            if (set == null || !set.contains(viewer.getUniqueId())) {
               list.add(player.getName());
            }
         }
      }

      return list;
   }

   private void msg(CommandSender sender, String key, String... replacements) {
      String s = this.config.getString("messages." + key);
      if (s != null && !s.isBlank()) {
         sender.sendMessage(Text.c(Text.apply(s, replacements)));
      } else {
         this.send(sender, key, replacements);
      }
   }
}

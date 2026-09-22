package com.sharded.core.modules.killstreaks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class KillstreaksModule extends Module implements CommandExecutor, TabCompleter {
   private KillstreakDatabase database;
   private final Map<UUID, Map<UUID, List<Long>>> recentKills = new ConcurrentHashMap<>();
   private final Map<UUID, List<Long>> recentSameIpKills = new ConcurrentHashMap<>();
   private final Map<UUID, List<Long>> recentVictimDeaths = new ConcurrentHashMap<>();

   public KillstreaksModule(ShardedCore plugin) {
      super(plugin, "killstreaks");
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new KillstreakDatabase(this.plugin, this.moduleFolder());
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open killstreak database", exception);
      }

      this.registerCommand("killstreak", this);
   }

   @Override
   protected void onDisable() {
      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
   }

   public KillstreakDatabase database() {
      return this.database;
   }

   public int streak(UUID uuid) {
      return this.database == null ? 0 : this.database.getCurrent(uuid);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("sharded.killstreak.use")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else {
         UUID uuid;
         String s;
         boolean flag;
         if (args.length == 0) {
            if (!(sender instanceof Player player2)) {
               this.send(sender, "players-only", new String[0]);
               return true;
            }

            uuid = player2.getUniqueId();
            s = player2.getName();
            flag = false;
         } else if (args.length == 1 && args[0].equalsIgnoreCase("best")) {
            if (!(sender instanceof Player player1)) {
               this.send(sender, "players-only", new String[0]);
               return true;
            }

            uuid = player1.getUniqueId();
            s = player1.getName();
            flag = true;
         } else if (args.length == 1) {
            if (!sender.hasPermission("sharded.killstreak.others")) {
               this.send(sender, "no-permission-others", new String[0]);
               return true;
            }

            OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
            uuid = offlineplayer.getUniqueId();
            s = offlineplayer.getName() == null ? args[0] : offlineplayer.getName();
            flag = false;
         } else {
            if (args.length != 2 || !args[1].equalsIgnoreCase("best")) {
               this.send(sender, "usage", new String[0]);
               return true;
            }

            if (!sender.hasPermission("sharded.killstreak.others")) {
               this.send(sender, "no-permission-others", new String[0]);
               return true;
            }

            OfflinePlayer offlineplayer1 = OfflinePlayers.resolve(args[0]);
            uuid = offlineplayer1.getUniqueId();
            s = offlineplayer1.getName() == null ? args[0] : offlineplayer1.getName();
            flag = true;
         }

         if (this.database == null) {
            return true;
         } else {
            int i = flag ? this.database.getBest(uuid) : this.database.getCurrent(uuid);
            if (sender instanceof Player player && player.getUniqueId().equals(uuid)) {
               this.send(sender, flag ? "self-best" : "self-current", new String[]{"%streak%", String.valueOf(i)});
               return true;
            }

            this.send(sender, flag ? "other-best" : "other-current", new String[]{"%player%", s, "%streak%", String.valueOf(i)});
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.killstreak.use")) {
         return List.of();
      } else if (args.length == 1) {
         List<String> list = new ArrayList<>();
         list.add("best");
         if (sender.hasPermission("sharded.killstreak.others")) {
            list.addAll(TabCompleteHelper.onlinePlayers(""));
         }

         return TabCompleteHelper.filter(args[0], list);
      } else {
         return args.length == 2 && sender.hasPermission("sharded.killstreak.others") ? TabCompleteHelper.filter(args[1], "best") : List.of();
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      Player player = event.getEntity();
      if (this.database != null) {
         this.database.setStreak(player.getUniqueId(), 0);
      }

      Player player1 = player.getKiller();
      if (player1 != null && !player1.equals(player) && this.database != null) {
         this.recordVictimDeath(player);
         if (this.shouldCountKill(player1, player)) {
            int i = this.database.getCurrent(player1.getUniqueId()) + 1;
            this.database.setStreak(player1.getUniqueId(), i);
            ConfigurationSection configurationsection = this.config.getConfigurationSection("rewards." + i);
            if (configurationsection != null) {
               String s = configurationsection.getString("broadcast", "");
               if (!s.isEmpty()) {
                  this.announce(Text.apply(s, "%player%", player1.getName(), "%streak%", String.valueOf(i)), player1);
               }

               this.send(player1, "milestone", new String[]{"%streak%", String.valueOf(i)});

               for (String s1 : configurationsection.getStringList("commands")) {
                  s1 = s1.replace("%player%", player1.getName()).replace("%streak%", String.valueOf(i)).replace("%uuid%", player1.getUniqueId().toString());
                  Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s1);
               }
            }
         }
      }
   }

   private void recordVictimDeath(Player victim) {
      if (this.config.getBoolean("anti-farm.enabled", true)) {
         long i = this.config.getLong("anti-farm.victim-death-window-seconds", 60L) * 1000L;
         long j = System.currentTimeMillis();
         List<Long> list = this.recentVictimDeaths.computeIfAbsent(victim.getUniqueId(), k -> new ArrayList<>());
         list.add(j);
         list.removeIf(t -> j - t > i);
      }
   }

   private boolean isVictimDeathFarming(Player victim) {
      if (!this.config.getBoolean("anti-farm.enabled", true)) {
         return false;
      } else {
         int i = this.config.getInt("anti-farm.victim-death-limit", 5);
         long j = this.config.getLong("anti-farm.victim-death-window-seconds", 60L) * 1000L;
         List<Long> list = this.recentVictimDeaths.get(victim.getUniqueId());
         if (list != null && !list.isEmpty()) {
            long k = System.currentTimeMillis();
            long l = list.stream().filter(t -> k - t <= j).count();
            return l >= (long)i;
         } else {
            return false;
         }
      }
   }

   private boolean shouldCountKill(Player killer, Player victim) {
      if (!this.config.getBoolean("anti-farm.enabled", true)) {
         return true;
      } else if (killer.hasPermission("sharded.killstreak.farm.bypass")) {
         return true;
      } else if (this.isVictimDeathFarming(victim)) {
         this.send(killer, "farm-victim-deaths", new String[]{"%player%", victim.getName()});
         return false;
      } else {
         long i = System.currentTimeMillis();
         if (this.config.getBoolean("anti-farm.track-same-ip", true)) {
            String s = killer.getAddress() == null ? null : killer.getAddress().getAddress().getHostAddress();
            String s1 = victim.getAddress() == null ? null : victim.getAddress().getAddress().getHostAddress();
            if (s != null && s1 != null && s.equals(s1)) {
               long j = this.config.getLong("anti-farm.same-ip-window-minutes", 60L) * 60000L;
               int k = this.config.getInt("anti-farm.same-ip-kill-limit", 2);
               List<Long> list = this.recentSameIpKills.computeIfAbsent(killer.getUniqueId(), kx -> new ArrayList<>());
               list.add(i);
               list.removeIf(t -> i - t > j);
               if (list.size() > k) {
                  this.send(killer, "farm-same-ip", new String[0]);
                  return false;
               }
            }
         }

         long l = this.config.getLong("anti-farm.window-minutes", 60L) * 60000L;
         int i1 = this.config.getInt("anti-farm.same-victim-limit", 3);
         Map<UUID, List<Long>> map = this.recentKills.computeIfAbsent(killer.getUniqueId(), k -> new ConcurrentHashMap<>());
         List<Long> list1 = map.computeIfAbsent(victim.getUniqueId(), k -> new ArrayList<>());
         list1.add(i);
         Iterator<Long> iterator = list1.iterator();

         while (iterator.hasNext()) {
            if (i - iterator.next() > l) {
               iterator.remove();
            }
         }

         if (list1.size() > i1) {
            this.send(killer, "farm-same-victim", new String[]{"%player%", victim.getName()});
            return false;
         } else {
            return true;
         }
      }
   }

   private void announce(String message, Player killer) {
      MessageUtil.Delivery messageutil$delivery = this.resolveDelivery("broadcast");
      Component component = Text.c(message);
      switch (messageutil$delivery) {
         case ACTIONBAR:
            killer.sendActionBar(component);

            for (Player player : Bukkit.getOnlinePlayers()) {
               if (player != killer) {
                  player.sendMessage(component);
               }
            }
            break;
         case BOTH:
            Bukkit.getServer().broadcast(component);
            killer.sendActionBar(component);
            break;
         default:
            Bukkit.getServer().broadcast(component);
      }
   }
}

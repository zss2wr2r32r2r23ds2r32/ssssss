package com.sharded.core.modules.arenasync;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public final class ArenaSyncModule extends Module implements CommandExecutor, TabCompleter {
   private final Set<Long> warned = new HashSet<>();
   private BukkitTask task;
   private long nextRunMillis;

   public ArenaSyncModule(ShardedCore plugin) {
      super(plugin, "arenasync");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("arenareset", this);
      this.scheduleNext(this.config.getLong("first-run-minutes", this.intervalMinutes()));
      if (this.config.getBoolean("run-on-start", false)) {
         this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> this.runCommands(), 200L);
      }

      this.task = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> this.tick(), 20L, 20L);
   }

   @Override
   protected void onDisable() {
      if (this.task != null) {
         this.task.cancel();
         this.task = null;
      }

      this.warned.clear();
   }

   private long intervalMinutes() {
      return Math.max(1L, this.config.getLong("interval-minutes", 10L));
   }

   private void scheduleNext(long minutes) {
      long i = Math.max(1L, minutes);
      this.nextRunMillis = System.currentTimeMillis() + i * 60000L;
      this.warned.clear();
   }

   private void tick() {
      long i = this.nextRunMillis - System.currentTimeMillis();
      if (i <= 0L) {
         this.runCommands();
         this.scheduleNext(this.intervalMinutes());
      } else {
         long j = (i + 999L) / 1000L;

         for (long k : this.config.getLongList("warn-seconds")) {
            if (j == k && this.warned.add(k)) {
               this.broadcast("warning", "%seconds%", String.valueOf(k));
            }
         }
      }
   }

   private void runCommands() {
      List<String> list = this.config.getStringList("commands");
      if (list.isEmpty()) {
         list = List.of("arenasync reset warzone");
      }

      for (String s : list) {
         if (s != null && !s.isBlank()) {
            String s1 = s.startsWith("/") ? s.substring(1) : s;

            try {
               Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s1);
               if (this.config.getBoolean("log", true)) {
                  this.plugin.getLogger().info("[ArenaSync] Ran '" + s1 + "'.");
               }
            } catch (Exception exception) {
               this.plugin.getLogger().warning("[ArenaSync] Command '" + s1 + "' failed: " + exception.getMessage());
            }
         }
      }

      this.broadcast("finished");
   }

   private void broadcast(String key, String... replacements) {
      if (this.config.getBoolean("broadcast-chat", true)) {
         String s = this.config.getString(key);
         if (s != null && !s.isBlank()) {
            String s1 = Text.apply(s, replacements);
            String s2 = this.config.getString("broadcast-world", "");

            for (Player player : Bukkit.getOnlinePlayers()) {
               if (s2 == null || s2.isBlank() || player.getWorld().getName().equalsIgnoreCase(s2)) {
                  player.sendMessage(Text.c(s1));
               }
            }
         }
      }
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("sharded.arenasync.admin")) {
         sender.sendMessage(Text.c(this.config.getString("no-permission", "&cYou cannot do that.")));
         return true;
      } else {
         String s = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
         if (s.equals("now")) {
            this.runCommands();
            this.scheduleNext(this.intervalMinutes());
            sender.sendMessage(Text.c(this.config.getString("reset-now", "&aArena reset command sent.")));
            return true;
         } else {
            long i = Math.max(0L, (this.nextRunMillis - System.currentTimeMillis()) / 1000L);
            sender.sendMessage(
               Text.c(
                  Text.apply(
                     this.config.getString("status", "&fNext arena reset in &a%time%&f (every &a%interval%m&f)."),
                     "%time%",
                     Text.time(i),
                     "%interval%",
                     String.valueOf(this.intervalMinutes())
                  )
               )
            );
            return true;
         }
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return args.length == 1 && sender.hasPermission("sharded.arenasync.admin")
         ? TabCompleteHelper.filter(args[0], new ArrayList<>(List.of("now", "status")))
         : List.of();
   }
}

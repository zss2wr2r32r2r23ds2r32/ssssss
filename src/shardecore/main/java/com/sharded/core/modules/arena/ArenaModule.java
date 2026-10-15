package com.sharded.core.modules.arena;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.protect.ProtectModule;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.TabCompleteHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;

public final class ArenaModule extends Module implements CommandExecutor, TabCompleter {
   private static final List<String> SIDE_ARENAS = List.of("side1", "side2", "side3", "side4");
   private ArenaService service;
   private BukkitTask autoResetTask;

   public ArenaModule(ShardedCore plugin) {
      super(plugin, "arena");
   }

   @Override
   protected void onEnable() {
      this.service = new ArenaService(this.plugin, this.moduleFolder());
      this.registerCommand("arena", this);
      this.startAutoReset();
   }

   @Override
   protected void onDisable() {
      if (this.autoResetTask != null) {
         this.autoResetTask.cancel();
      }
   }

   private void startAutoReset() {
      if (this.config.getBoolean("auto-reset.enabled", true)) {
         long i = this.config.getLong("auto-reset.interval-minutes", 15L);
         long j = Math.max(1200L, i * 60L * 20L);
         this.autoResetTask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> {
            if (this.config.getBoolean("auto-reset.enabled", true)) {
               this.resetSideArenas(true, null);
            }
         }, j, j);
      }
   }

   private CuboidRegion regionFor(String arenaId) {
      ProtectModule protectmodule = this.plugin.modules().get(ProtectModule.class);
      return protectmodule == null ? null : protectmodule.region(arenaId);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (args.length == 0) {
         this.send(sender, "usage", new String[0]);
         return true;
      } else {
         String s = args[0].toLowerCase(Locale.ROOT);
         if (s.equals("snapshot") && args.length >= 2) {
            if (!sender.hasPermission("sharded.arena.admin")) {
               this.send(sender, "no-permission", new String[0]);
               return true;
            } else {
               List<String> list1 = this.resolveArenaIds(args[1]);
               int i = 0;

               for (String s2 : list1) {
                  CuboidRegion cuboidregion = this.regionFor(s2);
                  if (cuboidregion == null) {
                     this.send(sender, "no-region", new String[]{"%arena%", s2});
                  } else {
                     i += this.service.snapshot(s2, cuboidregion);
                  }
               }

               this.send(sender, "snapshot-done", new String[]{"%count%", String.valueOf(i)});
               return true;
            }
         } else if (!s.equals("reset") || args.length < 2) {
            this.send(sender, "usage", new String[0]);
            return true;
         } else if (!sender.hasPermission("sharded.arena.admin")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else {
            boolean flag = args.length >= 3 && args[2].equalsIgnoreCase("fast");
            List<String> list = this.resolveArenaIds(args[1]);
            if (list.equals(SIDE_ARENAS)) {
               this.resetSideArenas(flag, sender);
            } else {
               for (String s1 : list) {
                  if (!this.service.hasSnapshot(s1)) {
                     this.send(sender, "no-snapshot", new String[]{"%arena%", s1});
                  } else {
                     this.service.reset(s1, flag, () -> this.send(sender, "reset-done", new String[]{"%arena%", s1}));
                  }
               }
            }

            return true;
         }
      }
   }

   private void resetSideArenas(boolean fast, CommandSender notify) {
      List<String> list = new ArrayList<>();

      for (String s : SIDE_ARENAS) {
         if (this.service.hasSnapshot(s)) {
            list.add(s);
         }
      }

      if (list.isEmpty()) {
         if (notify != null) {
            this.send(notify, "no-snapshots", new String[0]);
         }
      } else {
         this.service.resetAll(list, fast, idx -> {
            if (notify != null) {
               this.send(notify, "reset-done", new String[]{"%arena%", idx});
            }
         });
      }
   }

   private List<String> resolveArenaIds(String raw) {
      String s = raw.toLowerCase(Locale.ROOT);
      return !s.equals("side1-side4") && !s.equals("sides") && !s.equals("all") ? List.of(s) : SIDE_ARENAS;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.arena.admin")) {
         return List.of();
      } else if (args.length == 1) {
         return TabCompleteHelper.filter(args[0], "snapshot", "reset");
      } else if (args.length == 2) {
         List<String> list = new ArrayList<>(SIDE_ARENAS);
         list.add("side1-side4");
         return TabCompleteHelper.filter(args[1], list);
      } else {
         return args.length == 3 && args[0].equalsIgnoreCase("reset") ? TabCompleteHelper.filter(args[2], "fast") : List.of();
      }
   }
}

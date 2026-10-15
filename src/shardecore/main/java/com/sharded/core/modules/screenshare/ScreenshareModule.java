package com.sharded.core.modules.screenshare;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class ScreenshareModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<UUID, ScreenshareModule.Session> active = new ConcurrentHashMap<>();

   public ScreenshareModule(ShardedCore plugin) {
      super(plugin, "screenshare");
   }

   @Override
   protected void onEnable() {
      this.registerListener(this);
      this.registerCommand("screenshare", this);
   }

   @Override
   protected void onDisable() {
      for (ScreenshareModule.Session screensharemodule$session : this.active.values()) {
         if (screensharemodule$session.task() != null) {
            screensharemodule$session.task().cancel();
         }
      }

      this.active.clear();
   }

   public boolean isFrozen(UUID uuid) {
      return this.active.containsKey(uuid);
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!sender.hasPermission("sharded.screenshare.use") && !sender.hasPermission("sharded.staff.screenshare")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length == 0) {
         this.send(sender, "usage", new String[0]);
         return true;
      } else {
         Player player = Bukkit.getPlayerExact(args[0]);
         if (player == null) {
            this.send(sender, "not-online", new String[]{"%player%", args[0]});
            return true;
         } else if (this.active.containsKey(player.getUniqueId())) {
            this.stop(player, false);
            this.send(sender, "stopped", new String[]{"%player%", player.getName()});
            return true;
         } else if (sender instanceof Player player1) {
            this.start(player1, player);
            this.send(sender, "started", new String[]{"%player%", player.getName()});
            return true;
         } else {
            this.send(sender, "players-only", new String[0]);
            return true;
         }
      }
   }

   private void start(Player staff, Player target) {
      this.stop(target, false);
      long i = this.config.getLong("countdown-seconds", 120L);
      long j = System.currentTimeMillis() + i * 1000L;
      BukkitTask bukkittask = this.plugin.getServer().getScheduler().runTaskTimer(this.plugin, () -> this.tick(target), 20L, 20L);
      this.active.put(target.getUniqueId(), new ScreenshareModule.Session(staff.getUniqueId(), j, bukkittask));
      this.tick(target);
   }

   private void stop(Player target, boolean banOnQuit) {
      ScreenshareModule.Session screensharemodule$session = this.active.remove(target.getUniqueId());
      if (screensharemodule$session != null && screensharemodule$session.task() != null) {
         screensharemodule$session.task().cancel();
      }

      if (banOnQuit) {
         this.applyRefuseBan(target);
      }
   }

   private void tick(Player target) {
      ScreenshareModule.Session screensharemodule$session = this.active.get(target.getUniqueId());
      if (screensharemodule$session != null) {
         long i = Math.max(0L, (screensharemodule$session.endsAt() - System.currentTimeMillis()) / 1000L);
         if (i <= 0L) {
            this.stop(target, false);
         } else {
            if (this.config.getBoolean("action-bar.enabled", true)) {
               target.sendActionBar(Text.c(this.buildActionBar((int)i)));
            }

            if (i % this.config.getLong("chat-warning-interval-seconds", 5L) == 0L) {
               for (String s : this.messages.getStringList("messages.chat-warning-lines")) {
                  target.sendMessage(Text.c(Text.apply(s, "%seconds%", String.valueOf(i))));
               }
            }

            target.showTitle(
               Title.title(
                  Text.c(this.messages.getString("messages.title", "&#FF0000&lSCREENSHARE")),
                  Text.c(Text.apply(this.messages.getString("messages.subtitle", "&f%seconds%s left"), "%seconds%", String.valueOf(i))),
                  Times.times(Duration.ofMillis(0L), Duration.ofMillis(1100L), Duration.ofMillis(0L))
               )
            );
         }
      }
   }

   private String buildActionBar(int seconds) {
      int i = this.config.getInt("action-bar.bar-length", 20);
      int j = (int)Math.round((double)seconds / (double)this.config.getLong("countdown-seconds", 120L) * (double)i);
      String s = this.config.getString("action-bar.filled-color", "&#FF007B") + this.config.getString("action-bar.bar-char", "|").repeat(Math.max(0, j));
      String s1 = this.config.getString("action-bar.empty-color", "&#FF0000") + this.config.getString("action-bar.bar-char", "|").repeat(Math.max(0, i - j));
      return this.config.getString("action-bar.format", "%bar% &f%seconds%s").replace("%bar%", s + s1).replace("%seconds%", String.valueOf(seconds));
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onQuit(PlayerQuitEvent event) {
      if (this.active.containsKey(event.getPlayer().getUniqueId())) {
         this.stop(event.getPlayer(), true);
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      if (this.active.containsKey(event.getPlayer().getUniqueId())) {
         event.setCancelled(true);
         this.send(event.getPlayer(), "command-blocked", new String[0]);
      }
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onMove(PlayerMoveEvent event) {
      if (this.active.containsKey(event.getPlayer().getUniqueId())) {
         if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
         }
      }
   }

   private void applyRefuseBan(Player target) {
      PunishmentsModule punishmentsmodule = this.plugin.modules().get(PunishmentsModule.class);
      String s = this.config.getString("ban.reason", "SS-Refuse");
      String s1 = this.config.getString("ban.duration", "7d");
      if (punishmentsmodule != null) {
         punishmentsmodule.ban(Bukkit.getConsoleSender(), target, s, s1);
      } else {
         String s2 = this.config.getString("ban.fallback-command", "ban %player% SS-Refuse").replace("%player%", target.getName());
         Bukkit.dispatchCommand(Bukkit.getConsoleSender(), s2.startsWith("/") ? s2.substring(1) : s2);
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      return args.length == 1 ? TabCompleteHelper.onlinePlayers(args[0]) : List.of();
   }

   private static record Session(UUID staffId, long endsAt, BukkitTask task) {
   }
}

package com.sharded.core.modules.live;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class LiveModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
   private final Map<UUID, Boolean> toggled = new ConcurrentHashMap<>();

   public LiveModule(ShardedCore plugin) {
      super(plugin, "live");
   }

   @Override
   protected void onEnable() {
      this.registerCommand("live", this);
   }

   public void setLiveEnabled(Player player, boolean enabled) {
      this.toggled.put(player.getUniqueId(), enabled);
   }

   public boolean isLiveEnabled(Player player) {
      return this.toggled.getOrDefault(player.getUniqueId(), true);
   }

   @Override
   protected void onDisable() {
      this.cooldown.clear();
      this.toggled.clear();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player player) {
         if (!player.hasPermission("sharded.live.use")) {
            this.send(player, "no-permission", new String[0]);
            return true;
         } else if (args.length > 0 && args[0].equalsIgnoreCase("toggle")) {
            boolean flag = !this.toggled.getOrDefault(player.getUniqueId(), true);
            this.toggled.put(player.getUniqueId(), flag);
            this.send(player, flag ? "toggle-on" : "toggle-off", new String[0]);
            return true;
         } else if (args.length == 0) {
            this.send(player, "usage", new String[0]);
            return true;
         } else if (!this.toggled.getOrDefault(player.getUniqueId(), true)) {
            this.send(player, "disabled", new String[0]);
            return true;
         } else {
            String s = args[0];
            if (!s.startsWith("http://") && !s.startsWith("https://")) {
               s = "https://" + s;
            }

            String s1 = this.detectPlatform(s);
            if (!this.isAllowedPlatform(s, s1)) {
               this.send(player, "invalid-platform", new String[]{"%platform%", s1});
               return true;
            } else {
               long i = this.config.getLong("cooldown-seconds", 300L) * 1000L;
               long j = System.currentTimeMillis();
               Long olong = this.cooldown.get(player.getUniqueId());
               if (olong != null && olong > j) {
                  long k = Math.max(1L, (olong - j) / 1000L);
                  this.send(player, "cooldown", new String[]{"%seconds%", String.valueOf(k), "%time%", Text.time(k)});
                  return true;
               } else {
                  this.cooldown.put(player.getUniqueId(), j + i);
                  this.broadcast(player, s, s1);
                  this.send(player, "sent", new String[0]);
                  return true;
               }
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private void broadcast(Player player, String url, String platform) {
      List<String> list = this.config.getStringList("message");
      if (list.isEmpty()) {
         list = this.config.getStringList("messages.broadcast");
      }

      if (list.isEmpty()) {
         list = List.of("&#FF0000&lLIVE &8▷ &f%player% is live on %platform%! %link%");
      }

      for (String s : list) {
         Component component = this.clickable(s, player.getName(), platform, url);
         Bukkit.broadcast(component);
      }
   }

   private Component clickable(String raw, String playerName, String platform, String url) {
      String s = Text.apply(raw, "%player%", playerName, "%platform%", platform, "%link%", url);
      Component component = Text.c(s);
      if (raw.contains("%link%")) {
         component = component.clickEvent(ClickEvent.openUrl(url));
         String s1 = this.config.getString("link-hover", this.config.getString("messages.link-hover", "&fClick to open stream"));
         if (s1 != null && !s1.isBlank()) {
            component = component.hoverEvent(HoverEvent.showText(Text.c(Text.apply(s1, "%url%", url))));
         }
      }

      return component;
   }

   private boolean isAllowedPlatform(String url, String platform) {
      List<String> list = this.config.getStringList("platforms");
      if (list.isEmpty()) {
         return true;
      } else {
         String s = url.toLowerCase(Locale.ROOT);

         for (String s1 : list) {
            if (s1 != null && !s1.isBlank()) {
               String s2 = s1.toLowerCase(Locale.ROOT);
               if (s.contains(s2) || s2.equalsIgnoreCase(platform)) {
                  return true;
               }
            }
         }

         ConfigurationSection configurationsection = this.config.getConfigurationSection("platform-domains");
         if (configurationsection != null) {
            for (String s4 : configurationsection.getKeys(false)) {
               if (s4.equalsIgnoreCase(platform)) {
                  for (String s3 : configurationsection.getStringList(s4)) {
                     if (s.contains(s3.toLowerCase(Locale.ROOT))) {
                        return true;
                     }
                  }
               }
            }
         }

         return false;
      }
   }

   private String detectPlatform(String url) {
      String s = url.toLowerCase(Locale.ROOT);
      ConfigurationSection configurationsection = this.config.getConfigurationSection("platform-domains");
      if (configurationsection != null) {
         for (String s1 : configurationsection.getKeys(false)) {
            for (String s2 : configurationsection.getStringList(s1)) {
               if (s.contains(s2.toLowerCase(Locale.ROOT))) {
                  return s1;
               }
            }
         }
      }

      for (String s4 : this.config.getStringList("platforms")) {
         if (s4 != null && s.contains(s4.toLowerCase(Locale.ROOT))) {
            return s4.contains(".") ? s4.substring(0, s4.indexOf(46)) : s4;
         }
      }

      try {
         String s3 = URI.create(url).getHost();
         return s3 == null ? "unknown" : s3;
      } catch (Exception exception) {
         return "unknown";
      }
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (sender instanceof Player && sender.hasPermission("sharded.live.use")) {
         return args.length == 1 ? TabCompleteHelper.filter(args[0], "toggle") : List.of();
      } else {
         return List.of();
      }
   }
}

package com.sharded.core.modules.chatfilter;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class ChatFilterModule extends Module implements CommandExecutor, TabCompleter {
   private final Map<UUID, PlayerFilterState> states = new ConcurrentHashMap<>();
   private final Map<UUID, Long> npcInteractAt = new ConcurrentHashMap<>();
   private YamlConfiguration filterConfig;
   private YamlConfiguration blockedConfig;
   private YamlConfiguration historyConfig;
   private ChatFilterEngine engine;
   private FilterConfig settings = new FilterConfig();

   public ChatFilterModule(ShardedCore plugin) {
      super(plugin, "chatfilter");
   }

   @Override
   protected void onEnable() {
      this.reloadFilter();
      this.registerCommand("chatfilter", this);
      this.registerCommand("chathistory", this);
      this.registerListener(this);
   }

   @Override
   protected void onDisable() {
      this.states.clear();
      this.npcInteractAt.clear();
   }

   public void reloadFilter() {
      this.syncJarResource("config.yml");
      this.syncJarResource("blocked.yml");
      File file1 = this.moduleFolder();
      this.filterConfig = YamlConfiguration.loadConfiguration(new File(file1, "config.yml"));
      this.blockedConfig = YamlConfiguration.loadConfiguration(new File(file1, "blocked.yml"));
      File file2 = new File(file1, "chat-history.yml");
      this.historyConfig = file2.exists() ? YamlConfiguration.loadConfiguration(file2) : new YamlConfiguration();
      this.settings = this.readSettings();
      this.engine = new ChatFilterEngine(this.settings, this.readRules());
      this.pruneHistory();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (s.equals("chathistory")) {
         if (!sender.hasPermission("sharded.chatfilter.admin")) {
            this.send(sender, "no-permission", new String[0]);
            return true;
         } else {
            return this.history(sender, args);
         }
      } else if (!sender.hasPermission("sharded.chatfilter.admin")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else if (args.length != 0 && !args[0].equalsIgnoreCase("help")) {
         String s2 = args[0].toLowerCase(Locale.ROOT);

         return switch (s2) {
            case "test" -> this.test(sender, args);
            case "reload" -> {
               this.reloadFilter();
               this.send(sender, "reloaded", new String[0]);
               yield true;
            }
            default -> {
               this.send(sender, "usage", new String[0]);
               yield true;
            }
         };
      } else {
         for (String s1 : this.filterConfig.getStringList("messages.help")) {
            sender.sendMessage(Text.c(s1));
         }

         return true;
      }
   }

   private boolean test(CommandSender sender, String[] args) {
      if (args.length < 2) {
         this.send(sender, "usage-test", new String[0]);
         return true;
      } else {
         String s = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
         FilterVerdict filterverdict = this.engine.test(s);
         if (filterverdict.status() == FilterVerdict.Status.ALLOW) {
            String s1 = this.filterConfig.getString("messages.test-clean", "");
            if (s1 != null && !s1.isBlank()) {
               sender.sendMessage(Text.c(s1));
            }
         } else {
            String s2 = this.filterConfig.getString("messages.test-hit", "");
            if (s2 != null && !s2.isBlank()) {
               sender.sendMessage(
                  Text.c(
                     Text.apply(
                        s2,
                        "%rule%",
                        filterverdict.rule(),
                        "%action%",
                        filterverdict.action(),
                        "%result%",
                        filterverdict.blocked() ? filterverdict.original() : filterverdict.result()
                     )
                  )
               );
            }
         }

         return true;
      }
   }

   private boolean history(CommandSender sender, String[] args) {
      String s = args.length >= 1 ? args[0] : null;
      List<Map<String, Object>> list = this.historyEntries(s);
      if (list.isEmpty()) {
         this.send(sender, "history-empty", new String[0]);
         return true;
      } else {
         int i = Math.max(0, list.size() - 20);

         for (int j = i; j < list.size(); j++) {
            Map<String, Object> map = list.get(j);
            sender.sendMessage(
               Text.c("&#FF0000&lFILTER &7▷ &f" + map.get("player") + " &7» &f" + map.get("message") + " &8(" + map.get("rule") + " " + map.get("action") + ")")
            );
         }

         return true;
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onChat(AsyncChatEvent event) {
      if (this.settings.scanChat) {
         Player player = event.getPlayer();
         if (!player.hasPermission("sharded.chatfilter.bypass")) {
            String s = PlainTextComponentSerializer.plainText().serialize(event.message());
            FilterVerdict filterverdict = this.check(player, s);
            if (filterverdict.blocked()) {
               event.setCancelled(true);
               this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.apply(player, filterverdict));
            } else {
               if (filterverdict.modified()) {
                  event.message(Text.c(filterverdict.result()));
               }
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOW,
      ignoreCancelled = true
   )
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (!player.hasPermission("sharded.chatfilter.bypass")) {
         String s = event.getMessage();
         String s1 = this.commandLabel(s);
         if (this.isPrivateMessage(s1)) {
            if (!this.settings.scanPrivateMessages) {
               return;
            }
         } else if (!this.scansCommand(s1)) {
            return;
         }

         String s2 = this.commandPayload(s);
         if (!s2.isBlank()) {
            FilterVerdict filterverdict = this.check(player, s2);
            if (filterverdict.blocked()) {
               event.setCancelled(true);
               this.apply(player, filterverdict);
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST,
      ignoreCancelled = true
   )
   public void onNpcInteract(PlayerInteractEntityEvent event) {
      if (this.settings.npcCooldownEnabled) {
         Player player = event.getPlayer();
         if (!player.hasPermission("sharded.chatfilter.bypass")) {
            Entity entity = event.getRightClicked();
            if (this.isNpc(entity)) {
               long i = System.currentTimeMillis();
               long j = (long)Math.max(1, this.settings.npcCooldownSeconds) * 1000L;
               Long olong = this.npcInteractAt.get(player.getUniqueId());
               if (olong != null && i - olong < j) {
                  event.setCancelled(true);
                  long k = Math.max(1L, (j - (i - olong) + 999L) / 1000L);
                  String s = this.filterConfig
                     .getString("messages.npc-cooldown", "&#FF0000&lERROR &8▷ &fWait &#FF0000%seconds%s &fbefore talking to that NPC again.");
                  player.sendMessage(Text.c(Text.apply(s, "%seconds%", String.valueOf(k))));
               } else {
                  this.npcInteractAt.put(player.getUniqueId(), i);
               }
            }
         }
      }
   }

   private boolean isNpc(Entity entity) {
      if (entity == null) {
         return false;
      } else if (entity.hasMetadata("NPC")) {
         return true;
      } else {
         try {
            return entity.getPersistentDataContainer().getKeys().stream().anyMatch(k -> {
               String s = k.getKey().toLowerCase(Locale.ROOT);
               String s1 = k.getNamespace().toLowerCase(Locale.ROOT);
               return s.contains("npc") || s1.contains("citizen") || s1.contains("fancynpc");
            });
         } catch (Throwable throwable) {
            return false;
         }
      }
   }

   private FilterVerdict check(Player player, String message) {
      PlayerFilterState playerfilterstate = this.states.computeIfAbsent(player.getUniqueId(), id -> new PlayerFilterState());
      return this.engine.evaluate(message, System.currentTimeMillis(), playerfilterstate);
   }

   private void apply(Player player, FilterVerdict verdict) {
      this.sendDenied(player, verdict);
      boolean flag = "CANCEL".equals(verdict.action()) && !verdict.isCheck() || "MASK".equals(verdict.action()) || "WARN".equals(verdict.action());
      if (this.settings.alertsEnabled && (flag || this.settings.alertChecks && verdict.isCheck())) {
         this.alert(player, verdict);
      }

      if (this.settings.logEnabled && (flag || !this.settings.logRulesOnly && verdict.blocked())) {
         this.log(player, verdict);
      }

      if ((verdict.blocked() || "WARN".equals(verdict.action())) && this.settings.soundBlockedEnabled) {
         player.playSound(player.getLocation(), this.settings.soundBlocked, this.settings.soundBlockedVolume, this.settings.soundBlockedPitch);
      }
   }

   private void sendDenied(Player player, FilterVerdict verdict) {
      String s1 = verdict.rule();

      String s = switch (s1) {
         case "slowmode" -> "slowmode";
         case "length" -> "too-long";
         case "spamming" -> "spamming";
         case "repeat" -> "repeating";
         case "shouting" -> "shouting";
         default -> "blocked";
      };
      s1 = this.filterConfig.getString("messages." + s);
      if (s1 != null && !s1.isBlank()) {
         String s2 = switch (s) {
            case "slowmode" -> Text.apply(s1, "%seconds%", String.valueOf(verdict.waitSeconds()));
            case "too-long" -> Text.apply(s1, "%max%", String.valueOf(verdict.maxCharacters()));
            default -> s1;
         };
         if (this.settings.actionbar) {
            player.sendActionBar(Text.c(s2));
         } else {
            player.sendMessage(Text.c(s2));
         }
      } else {
         this.send(player, s, new String[0]);
      }
   }

   private void alert(Player player, FilterVerdict verdict) {
      Component component = Text.c(
         Text.apply(
            this.settings.alertFormat, "%player%", player.getName(), "%message%", verdict.original(), "%rule%", verdict.rule(), "%action%", verdict.action()
         )
      );
      Bukkit.getConsoleSender().sendMessage(component);

      for (Player playerx : Bukkit.getOnlinePlayers()) {
         if (playerx.hasPermission("sharded.chatfilter.alerts")) {
            playerx.sendMessage(component);
         }
      }
   }

   private void log(Player player, FilterVerdict verdict) {
      List<Map<String, Object>> list = this.historyEntries(null);
      list.add(
         Map.of(
            "time",
            Instant.now().toString(),
            "player",
            player.getName(),
            "uuid",
            player.getUniqueId().toString(),
            "message",
            verdict.original(),
            "rule",
            verdict.rule(),
            "action",
            verdict.action(),
            "result",
            verdict.result()
         )
      );
      this.historyConfig.set("entries", list);
      this.saveHistory();
   }

   private List<Map<String, Object>> historyEntries(String playerName) {
      List<Map<String, Object>> list = new ArrayList<>();

      for (Object object : this.historyConfig.getList("entries", List.of())) {
         if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>)object;
            Map<String, Object> map1 = new LinkedHashMap<>();
            map.forEach((k, v) -> map1.put(String.valueOf(k), v));
            if (playerName == null || playerName.isBlank() || playerName.equalsIgnoreCase(String.valueOf(map1.get("player")))) {
               list.add(map1);
            }
         }
      }

      return list;
   }

   private void pruneHistory() {
      long i = System.currentTimeMillis() - (long)this.settings.keepDays * 86400000L;
      List<Map<String, Object>> list = this.historyEntries(null);
      Iterator<Map<String, Object>> iterator = list.iterator();

      while (iterator.hasNext()) {
         try {
            if (Instant.parse(String.valueOf(iterator.next().get("time"))).toEpochMilli() < i) {
               iterator.remove();
            }
         } catch (RuntimeException runtimeexception) {
         }
      }

      this.historyConfig.set("entries", list);
      this.saveHistory();
   }

   private void saveHistory() {
      try {
         this.historyConfig.save(new File(this.moduleFolder(), "chat-history.yml"));
      } catch (IOException ioexception) {
      }
   }

   private FilterConfig readSettings() {
      FilterConfig filterconfig = new FilterConfig();
      filterconfig.scanChat = this.filterConfig.getBoolean("scan.chat", true);
      filterconfig.scanPrivateMessages = this.filterConfig.getBoolean("scan.private-messages", true);
      filterconfig.scanCommands = new ArrayList<>(this.filterConfig.getStringList("scan.commands"));
      filterconfig.slowmodeEnabled = this.filterConfig.getBoolean("slowmode.enabled", true);
      filterconfig.slowmodeSeconds = this.filterConfig.getInt("slowmode.seconds", 3);
      filterconfig.npcCooldownEnabled = this.filterConfig.getBoolean("npc-interact.enabled", true);
      filterconfig.npcCooldownSeconds = this.filterConfig.getInt("npc-interact.seconds", 2);
      filterconfig.lengthEnabled = this.filterConfig.getBoolean("length.enabled", true);
      filterconfig.maxCharacters = this.filterConfig.getInt("length.max-characters", 128);
      filterconfig.maxSameInARow = this.filterConfig.getInt("length.max-same-in-a-row", 5);
      filterconfig.repeatEnabled = this.filterConfig.getBoolean("repeat.enabled", true);
      filterconfig.rememberSeconds = this.filterConfig.getInt("repeat.remember-seconds", 30);
      filterconfig.matchPercent = this.filterConfig.getInt("repeat.match-percent", 80);
      filterconfig.shoutingEnabled = this.filterConfig.getBoolean("shouting.enabled", true);
      filterconfig.maxUppercase = this.filterConfig.getInt("shouting.max-uppercase", 8);
      filterconfig.shoutingAction = FilterConfig.ShoutingAction.parse(this.filterConfig.getString("shouting.action"));
      filterconfig.wordsEnabled = this.filterConfig.getBoolean("words.enabled", true);
      filterconfig.mask = this.filterConfig.getString("words.mask", "***");
      filterconfig.alertsEnabled = this.filterConfig.getBoolean("alerts.enabled", true);
      filterconfig.alertChecks = this.filterConfig.getBoolean("alerts.checks", false);
      filterconfig.alertFormat = this.filterConfig.getString("alerts.format", filterconfig.alertFormat);
      filterconfig.logEnabled = this.filterConfig.getBoolean("log.enabled", true);
      filterconfig.logRulesOnly = this.filterConfig.getBoolean("log.rules-only", true);
      filterconfig.keepDays = this.filterConfig.getInt("log.keep-days", 14);
      filterconfig.soundBlockedEnabled = this.filterConfig.getBoolean("sounds.blocked.enabled", true);
      filterconfig.soundBlocked = this.filterConfig.getString("sounds.blocked.sound", "block.note_block.bass");
      filterconfig.soundBlockedVolume = (float)this.filterConfig.getDouble("sounds.blocked.volume", 0.8);
      filterconfig.soundBlockedPitch = (float)this.filterConfig.getDouble("sounds.blocked.pitch", 0.8);
      filterconfig.actionbar = this.filterConfig.getBoolean("messages.actionbar", false);
      return filterconfig;
   }

   private List<WordRule> readRules() {
      List<WordRule> list = new ArrayList<>();
      ConfigurationSection configurationsection = this.blockedConfig.getConfigurationSection("rules");
      if (configurationsection == null) {
         return list;
      } else {
         for (String s : configurationsection.getKeys(false)) {
            ConfigurationSection configurationsection1 = configurationsection.getConfigurationSection(s);
            if (configurationsection1 != null && configurationsection1.getBoolean("enabled", true)) {
               List<String> list1 = new ArrayList<>(configurationsection1.getStringList("patterns"));
               list1.addAll(configurationsection1.getStringList("regex"));
               list.add(
                  new WordRule(
                     s, WordRule.Action.parse(configurationsection1.getString("action", "CANCEL")), configurationsection1.getStringList("words"), list1
                  )
               );
            }
         }

         return list;
      }
   }

   private boolean scansCommand(String label) {
      for (String s : this.settings.scanCommands) {
         if (this.commandLabel("/" + s).equalsIgnoreCase(label)) {
            return true;
         }
      }

      return false;
   }

   private boolean isPrivateMessage(String label) {
      String s = label.toLowerCase(Locale.ROOT);

      return switch (s) {
         case "msg", "tell", "w", "whisper", "r", "reply", "message", "pm", "dm", "m", "t" -> true;
         default -> false;
      };
   }

   private String commandLabel(String message) {
      String s = message.startsWith("/") ? message.substring(1) : message;
      int i = s.indexOf(32);
      String s1 = i >= 0 ? s.substring(0, i) : s;
      int j = s1.indexOf(58);
      return j >= 0 ? s1.substring(j + 1) : s1;
   }

   private String commandPayload(String message) {
      String s = message.startsWith("/") ? message.substring(1) : message;
      int i = s.indexOf(32);
      return i < 0 ? "" : s.substring(i + 1).trim();
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!sender.hasPermission("sharded.chatfilter.admin")) {
         return List.of();
      } else if (command.getName().equalsIgnoreCase("chathistory") && args.length == 1) {
         List<String> list = new ArrayList<>();

         for (Player player : Bukkit.getOnlinePlayers()) {
            list.add(player.getName());
         }

         return TabCompleteHelper.filter(args[0], list);
      } else {
         return args.length == 1 ? TabCompleteHelper.filter(args[0], "test", "reload", "help") : List.of();
      }
   }
}

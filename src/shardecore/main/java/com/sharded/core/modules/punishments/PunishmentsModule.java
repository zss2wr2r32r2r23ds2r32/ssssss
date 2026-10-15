package com.sharded.core.modules.punishments;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.killstreaks.KillstreaksModule;
import com.sharded.core.modules.spawnselect.SpawnSelectModule;
import com.sharded.core.util.ColorUtil;
import com.sharded.core.util.DiscordWebhook;
import com.sharded.core.util.DurationUtil;
import com.sharded.core.util.ItemBuilder;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TrackedInventories;
import com.sharded.core.util.VanillaBanHelper;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.Title.Times;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;

public final class PunishmentsModule extends Module implements CommandExecutor, TabCompleter {
   private PunishmentDatabase database;
   private final Map<UUID, PunishmentsModule.GuiSession> sessions = new ConcurrentHashMap<>();
   private final Map<UUID, PunishmentsModule.CachedMute> muteCache = new ConcurrentHashMap<>();

   public PunishmentsModule(ShardedCore plugin) {
      super(plugin, "punishments");
   }

   public PunishmentDatabase database() {
      return this.database;
   }

   private boolean can(CommandSender sender, String punishNode) {
      if (sender.hasPermission("sharded.admin")) {
         return true;
      } else if (sender.hasPermission(punishNode)) {
         return true;
      } else {
         return punishNode.startsWith("sharded.punishments.")
            ? sender.hasPermission("sharded.staff." + punishNode.substring("sharded.punishments.".length()))
            : false;
      }
   }

   @Override
   protected void onEnable() {
      try {
         this.database = new PunishmentDatabase(this.plugin, this.moduleFolder());
      } catch (Exception exception) {
         throw new IllegalStateException("Could not open punishments database", exception);
      }

      this.registerListener(this);
      this.registerCommand("punish", this);
      this.registerCommand("ban", this);
      this.registerCommand("banip", this);
      this.registerCommand("kick", this);
      this.registerCommand("mute", this);
      this.registerCommand("offend", this);
      this.registerCommand("unban", this);
      this.registerCommand("unbanip", this);
      this.registerCommand("unmute", this);
      this.registerCommand("pardon", this);
      this.registerCommand("wipe", this);
      this.registerCommand("alts", this);
      this.registerCommand("revokepunishment", this);
   }

   @Override
   protected void onDisable() {
      if (this.database != null) {
         this.database.close();
      }

      this.database = null;
      this.sessions.clear();
      this.muteCache.clear();
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);

      return switch (s) {
         case "punish" -> this.handlePunishCmd(sender, args);
         case "ban" -> this.handleBanCmd(sender, args);
         case "banip" -> this.handleBanIpCmd(sender, args);
         case "kick" -> this.handleKickCmd(sender, args);
         case "mute" -> this.handleMuteCmd(sender, args);
         case "offend" -> this.handleOffendCmd(sender, args);
         case "unban" -> this.handleUnbanCmd(sender, args);
         case "unbanip" -> this.handleUnbanIpCmd(sender, args);
         case "unmute" -> this.handleUnmuteCmd(sender, args);
         case "pardon" -> this.handlePardonCmd(sender, args);
         case "wipe" -> this.handleWipeCmd(sender, args);
         case "alts" -> this.handleAltsCmd(sender, args);
         case "revokepunishment" -> this.handleRevokePunishmentCmd(sender);
         default -> false;
      };
   }

   private boolean handlePunishCmd(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.send(player, "punish-usage", new String[0]);
            return true;
         } else {
            OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
            if (offlineplayer == null) {
               this.send(player, "player-not-found", new String[0]);
               return true;
            } else {
               this.openPunishMenu(player, offlineplayer);
               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean handleBanCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "ban-usage", new String[0]);
         return true;
      } else {
         OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
         if (offlineplayer == null) {
            this.send(sender, "player-not-found", new String[0]);
            return true;
         } else {
            String s = args.length >= 2
               ? this.joinArgs(args, 1, args.length - (args.length >= 3 ? 1 : 0))
               : this.config.getString("default-reason", "Unfair Modifications");
            String s1 = args.length >= 3 ? args[args.length - 1] : this.defaultDuration("reasons", s);
            if (args.length == 2 && this.config.getConfigurationSection("reasons") != null && this.config.getConfigurationSection("reasons").contains(s)) {
               s1 = this.defaultDuration("reasons", s);
            }

            this.ban(sender, offlineplayer, s, s1);
            return true;
         }
      }
   }

   private boolean handleBanIpCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "banip-usage", new String[0]);
         return true;
      } else {
         OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
         if (offlineplayer == null) {
            this.send(sender, "player-not-found", new String[0]);
            return true;
         } else {
            String s = args.length >= 2 ? args[1] : this.config.getString("default-reason", "Unfair Modifications");
            String s1 = args.length >= 3 ? args[2] : this.defaultDuration("reasons", s);
            this.banIp(sender, offlineplayer, s, s1);
            return true;
         }
      }
   }

   private boolean handleMuteCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "mute-usage", new String[0]);
         return true;
      } else {
         OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
         if (offlineplayer == null) {
            this.send(sender, "player-not-found", new String[0]);
            return true;
         } else {
            String s = args.length >= 2
               ? this.joinArgs(args, 1, args.length - (args.length >= 3 ? 1 : 0))
               : this.config.getString("mute.default-reason", "Spam");
            String s1 = args.length >= 3 ? args[args.length - 1] : this.defaultDuration("mute-reasons", s);
            if (args.length == 2
               && this.config.getConfigurationSection("mute-reasons") != null
               && this.config.getConfigurationSection("mute-reasons").contains(s)) {
               s1 = this.defaultDuration("mute-reasons", s);
            }

            this.mute(sender, offlineplayer, s, s1);
            return true;
         }
      }
   }

   private boolean handleKickCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "kick-usage", new String[0]);
         return true;
      } else {
         Player player = Bukkit.getPlayerExact(args[0]);
         if (player == null) {
            this.send(sender, "player-not-found", new String[0]);
            return true;
         } else {
            String s = args.length >= 2 ? this.joinArgs(args, 1) : this.config.getString("kick.default-reason", "No reason specified");
            this.kick(sender, player, s);
            return true;
         }
      }
   }

   private boolean handleOffendCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "offend-usage", new String[0]);
         return true;
      } else {
         OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
         if (offlineplayer == null) {
            this.send(sender, "player-not-found", new String[0]);
            return true;
         } else {
            String s = args.length >= 2
               ? this.joinArgs(args, 1, args.length - (args.length >= 3 ? 1 : 0))
               : this.config.getString("offend.reason", "Ban-Evasion");
            String s1 = args.length >= 3 ? args[args.length - 1] : this.config.getString("offend.duration", "permanent");
            if (args.length == 2 && this.config.getConfigurationSection("reasons") != null && this.config.getConfigurationSection("reasons").contains(s)) {
               s1 = this.defaultDuration("reasons", s);
            }

            this.ban(sender, offlineplayer, s, s1);
            return true;
         }
      }
   }

   private boolean handleRevokePunishmentCmd(CommandSender sender) {
      if (!this.can(sender, "sharded.punishments.revokepunishment")) {
         this.send(sender, "no-permission", new String[0]);
         return true;
      } else {
         int i = this.database.revokeActiveBansExceptDoxxing();
         int j = this.database.revokeActiveMutes();
         int k = this.database.revokeActiveWarnings();
         int l = this.database.deleteKicks();
         int i1 = this.database.deleteHistory();
         this.database.revokeAllIpBans();

         for (String s : VanillaBanHelper.vanillaIpBans()) {
            VanillaBanHelper.pardonIp(s);
         }

         for (String s2 : VanillaBanHelper.vanillaNameBans()) {
            VanillaBanHelper.pardonName(s2);
         }

         String s1 = this.config.getString("revoke-console-scope", "server:global");
         this.plugin.getLogger().info("Removed " + i + " bans from " + s1 + ".");
         this.plugin.getLogger().info("Removed " + j + " mutes from " + s1 + ".");
         this.plugin.getLogger().info("Removed " + k + " warnings from " + s1 + ".");
         this.plugin.getLogger().info("Removed " + l + " kicks from " + s1 + ".");
         this.plugin.getLogger().info("Removed " + i1 + " history from " + s1 + ".");
         this.send(
            sender,
            "revoke-done",
            new String[]{
               "%bans%",
               String.valueOf(i),
               "%mutes%",
               String.valueOf(j),
               "%warnings%",
               String.valueOf(k),
               "%kicks%",
               String.valueOf(l),
               "%history%",
               String.valueOf(i1)
            }
         );
         return true;
      }
   }

   private boolean handleUnbanCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "unban-usage", new String[0]);
         return true;
      } else {
         OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
         if (offlineplayer == null) {
            this.send(sender, "player-not-found", new String[0]);
            return true;
         } else {
            this.unban(sender, offlineplayer);
            return true;
         }
      }
   }

   private boolean handleUnbanIpCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "unbanip-usage", new String[0]);
         return true;
      } else if (args[0].equalsIgnoreCase("list")) {
         this.listIpBans(sender);
         return true;
      } else {
         String s = args[0].trim();
         OfflinePlayer offlineplayer = OfflinePlayers.resolve(s);
         if (offlineplayer != null) {
            this.clearAllBansForPlayer(sender, offlineplayer);
            return true;
         } else {
            this.unbanIp(sender, s);
            return true;
         }
      }
   }

   private void listIpBans(CommandSender sender) {
      if (!this.can(sender, "sharded.punishments.unbanip")) {
         this.send(sender, "no-permission", new String[0]);
      } else {
         List<String> list = this.database.activeIpBans();
         List<String> list1 = this.database.activePunishedPlayerNames(PunishmentDatabase.PunishmentType.BAN);
         List<String> list2 = VanillaBanHelper.vanillaIpBans();
         List<String> list3 = VanillaBanHelper.vanillaNameBans();
         if (list.isEmpty() && list1.isEmpty() && list2.isEmpty() && list3.isEmpty()) {
            this.send(sender, "unbanip-list-empty", new String[0]);
         } else {
            if (!list.isEmpty()) {
               this.send(sender, "unbanip-list-header", new String[]{"%count%", String.valueOf(list.size())});

               for (String s : list) {
                  sender.sendMessage(Text.c(this.messagePrefix() + "&7- &f" + s + " &8(/unbanip " + s + ")"));
               }
            }

            if (!list2.isEmpty()) {
               this.send(sender, "unbanip-vanilla-header", new String[]{"%count%", String.valueOf(list2.size())});

               for (String s1 : list2) {
                  sender.sendMessage(Text.c(this.messagePrefix() + "&7- &c" + s1 + " &8(vanilla — /unbanip " + s1 + ")"));
               }
            }

            if (!list1.isEmpty()) {
               this.send(sender, "unban-list-header", new String[]{"%count%", String.valueOf(list1.size())});

               for (String s2 : list1) {
                  sender.sendMessage(Text.c(this.messagePrefix() + "&7- &f" + s2 + " &8(/unban " + s2 + ")"));
               }
            }

            if (!list3.isEmpty()) {
               this.send(sender, "unban-vanilla-header", new String[]{"%count%", String.valueOf(list3.size())});

               for (String s3 : list3) {
                  sender.sendMessage(Text.c(this.messagePrefix() + "&7- &c" + s3 + " &8(vanilla — /unban " + s3 + ")"));
               }
            }

            if (!list1.isEmpty() || !list3.isEmpty()) {
               this.send(sender, "unbanip-hint-player-ban", new String[0]);
            }
         }
      }
   }

   private void pardonVanillaForPlayer(String playerName, List<String> ips) {
      VanillaBanHelper.pardonName(playerName);
      if (ips != null) {
         for (String s : ips) {
            VanillaBanHelper.pardonIp(s);
         }
      }
   }

   private boolean handleUnmuteCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "unmute-usage", new String[0]);
         return true;
      } else {
         this.unmute(sender, OfflinePlayers.resolve(args[0]));
         return true;
      }
   }

   private boolean handlePardonCmd(CommandSender sender, String[] args) {
      if (args.length == 0) {
         this.send(sender, "pardon-usage", new String[0]);
         return true;
      } else {
         this.pardon(sender, OfflinePlayers.resolve(args[0]));
         return true;
      }
   }

   private boolean handleWipeCmd(CommandSender sender, String[] args) {
      if (sender instanceof Player player) {
         if (args.length == 0) {
            this.send(player, "wipe-usage", new String[0]);
            return true;
         } else {
            OfflinePlayer offlineplayer = OfflinePlayers.resolve(args[0]);
            if (offlineplayer == null) {
               this.send(player, "player-not-found", new String[0]);
               return true;
            } else {
               String s = args.length >= 2 ? args[1] : "default";
               if (this.config.getBoolean("wipe.confirm-gui", true)) {
                  this.openWipeConfirm(player, offlineplayer, s);
               } else {
                  this.wipePlayer(player, offlineplayer.getUniqueId(), OfflinePlayers.name(offlineplayer.getUniqueId()), s);
               }

               return true;
            }
         }
      } else {
         this.send(sender, "players-only", new String[0]);
         return true;
      }
   }

   private boolean handleAltsCmd(CommandSender sender, String[] args) {
      OfflinePlayer offlineplayer = (OfflinePlayer)(args.length == 0 && sender instanceof Player player ? player : OfflinePlayers.resolve(args[0]));
      if (offlineplayer == null) {
         this.send(sender, args.length == 0 ? "alts-usage" : "player-not-found", new String[0]);
         return true;
      } else {
         this.showAlts(sender, offlineplayer);
         return true;
      }
   }

   private String joinArgs(String[] args, int from) {
      return this.joinArgs(args, from, args.length);
   }

   private String joinArgs(String[] args, int from, int to) {
      StringBuilder stringbuilder = new StringBuilder();

      for (int i = from; i < to; i++) {
         if (i > from) {
            stringbuilder.append(' ');
         }

         stringbuilder.append(args[i]);
      }

      return stringbuilder.toString();
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      String s = command.getName().toLowerCase(Locale.ROOT);
      if (args.length == 1) {
         return switch (s) {
            case "unban" -> TabCompleteHelper.filter(args[0], this.database.activePunishedPlayerNames(PunishmentDatabase.PunishmentType.BAN));
            case "unbanip" -> {
               if ("list".startsWith(args[0].toLowerCase(Locale.ROOT))) {
                  yield TabCompleteHelper.filter(args[0], "list");
               } else {
                  List<String> list1 = new ArrayList<>(this.database.activeIpBans());
                  list1.addAll(VanillaBanHelper.vanillaIpBans());

                  for (String s2 : this.database.knownPlayerNames()) {
                     if (!list1.contains(s2)) {
                        list1.add(s2);
                     }
                  }

                  yield TabCompleteHelper.filter(args[0], list1);
               }
            }
            case "unmute" -> TabCompleteHelper.filter(args[0], this.database.activePunishedPlayerNames(PunishmentDatabase.PunishmentType.MUTE));
            case "pardon" -> {
               List<String> list = new ArrayList<>(this.database.activePunishedPlayerNames(PunishmentDatabase.PunishmentType.BAN));

               for (String s1 : this.database.activePunishedPlayerNames(PunishmentDatabase.PunishmentType.MUTE)) {
                  if (!list.contains(s1)) {
                     list.add(s1);
                  }
               }

               yield TabCompleteHelper.filter(args[0], list);
            }
            case "kick" -> TabCompleteHelper.onlinePlayers(args[0]);
            default -> TabCompleteHelper.knownPlayers(args[0]);
         };
      } else if (args.length == 2) {
         return switch (s) {
            case "ban", "banip", "offend" -> TabCompleteHelper.configKeys(args[1], this.banReasons());
            case "mute" -> TabCompleteHelper.configKeys(args[1], this.muteReasons());
            case "kick" -> TabCompleteHelper.configKeys(args[1], this.kickReasons());
            case "wipe" -> TabCompleteHelper.configKeys(args[1], this.wipeReasons());
            default -> List.of();
         };
      } else {
         return List.of();
      }
   }

   private String defaultDuration(String sectionPath, String reason) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection(sectionPath);
      if (configurationsection != null && configurationsection.contains(reason)) {
         Object object = configurationsection.get(reason);
         if (object instanceof List<?> list && !list.isEmpty()) {
            return String.valueOf(list.get(0));
         }

         return object == null ? "permanent" : String.valueOf(object);
      } else {
         return "permanent";
      }
   }

   public List<String> kickReasons() {
      return this.config.getStringList("kick-reasons");
   }

   public void openPunishMenu(Player staff, Player target) {
      if (!this.can(staff, "sharded.punishments.punish")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         this.openPunishMain(staff, target.getUniqueId(), target.getName());
      }
   }

   public void openPunishMenu(Player staff, OfflinePlayer target) {
      if (!this.can(staff, "sharded.punishments.punish")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         this.openPunishMain(staff, target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()));
      }
   }

   private void openPunishMain(Player staff, UUID targetId, String targetName) {
      int i = 3;
      PunishmentsModule.PunishHolder punishmentsmodule$punishholder = new PunishmentsModule.PunishHolder(PunishmentsModule.GuiType.PUNISH_MAIN, targetId);
      String s = Text.apply(this.config.getString("punish.menu.title", "&8Punish | %player%"), "%player%", targetName);
      Inventory inventory = Bukkit.createInventory(punishmentsmodule$punishholder, i * 9, Text.c(s));
      TrackedInventories.track(inventory, punishmentsmodule$punishholder);
      this.fill(inventory);
      inventory.setItem(11, this.menuItem("punish.menu.ban", targetName, "ban"));
      inventory.setItem(13, this.head(targetName));
      inventory.setItem(15, this.menuItem("punish.menu.mute", targetName, "mute"));
      inventory.setItem(16, this.menuItem("punish.menu.ipban", targetName, "ipban"));
      staff.openInventory(inventory);
      this.sessions.put(staff.getUniqueId(), new PunishmentsModule.GuiSession(PunishmentsModule.GuiType.PUNISH_MAIN, targetId, null, null, null));
   }

   private void openReasonMenu(Player staff, UUID targetId, String targetName, String type) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.RuntimeException: invalid constant type: Ljava/util/List; with value mute
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.ConstExprent.toJava(ConstExprent.java:356)
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.SwitchExprent.toJava(SwitchExprent.java:104)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.getCastedExprent(ExprProcessor.java:1018)
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.AssignmentExprent.toJava(AssignmentExprent.java:154)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.listToJava(ExprProcessor.java:895)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.BasicBlockStatement.toJava(BasicBlockStatement.java:90)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.IfStatement.toJava(IfStatement.java:203)
      //   at org.jetbrains.java.decompiler.modules.decompiler.ExprProcessor.jmpWrapper(ExprProcessor.java:833)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.SequenceStatement.toJava(SequenceStatement.java:107)
      //   at org.jetbrains.java.decompiler.modules.decompiler.stats.RootStatement.toJava(RootStatement.java:36)
      //   at org.jetbrains.java.decompiler.main.ClassWriter.writeMethod(ClassWriter.java:1283)
      //
      // Bytecode:
      // 000: aload 4
      // 002: getstatic java/util/Locale.ROOT Ljava/util/Locale;
      // 005: invokevirtual java/lang/String.toLowerCase (Ljava/util/Locale;)Ljava/lang/String;
      // 008: astore 6
      // 00a: bipush -1
      // 00b: istore 7
      // 00d: aload 6
      // 00f: invokevirtual java/lang/String.hashCode ()I
      // 012: lookupswitch 56 2 3363353 26 100403592 42
      // 02c: aload 6
      // 02e: ldc "mute"
      // 030: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 033: ifeq 04a
      // 036: bipush 0
      // 037: istore 7
      // 039: goto 04a
      // 03c: aload 6
      // 03e: ldc_w "ipban"
      // 041: invokevirtual java/lang/String.equals (Ljava/lang/Object;)Z
      // 044: ifeq 04a
      // 047: bipush 1
      // 048: istore 7
      // 04a: iload 7
      // 04c: lookupswitch 52 2 0 28 1 40
      // 068: aload 0
      // 069: getfield com/sharded/core/modules/punishments/PunishmentsModule.config Lorg/bukkit/configuration/file/YamlConfiguration;
      // 06c: ldc "mute-reasons"
      // 06e: invokevirtual org/bukkit/configuration/file/YamlConfiguration.getConfigurationSection (Ljava/lang/String;)Lorg/bukkit/configuration/ConfigurationSection;
      // 071: goto 089
      // 074: aload 0
      // 075: getfield com/sharded/core/modules/punishments/PunishmentsModule.config Lorg/bukkit/configuration/file/YamlConfiguration;
      // 078: ldc "reasons"
      // 07a: invokevirtual org/bukkit/configuration/file/YamlConfiguration.getConfigurationSection (Ljava/lang/String;)Lorg/bukkit/configuration/ConfigurationSection;
      // 07d: goto 089
      // 080: aload 0
      // 081: getfield com/sharded/core/modules/punishments/PunishmentsModule.config Lorg/bukkit/configuration/file/YamlConfiguration;
      // 084: ldc "reasons"
      // 086: invokevirtual org/bukkit/configuration/file/YamlConfiguration.getConfigurationSection (Ljava/lang/String;)Lorg/bukkit/configuration/ConfigurationSection;
      // 089: astore 5
      // 08b: aload 5
      // 08d: ifnonnull 09d
      // 090: aload 0
      // 091: aload 1
      // 092: ldc_w "no-reasons"
      // 095: bipush 0
      // 096: anewarray 38
      // 099: invokevirtual com/sharded/core/modules/punishments/PunishmentsModule.send (Lorg/bukkit/command/CommandSender;Ljava/lang/String;[Ljava/lang/String;)V
      // 09c: return
      // 09d: new java/util/ArrayList
      // 0a0: dup
      // 0a1: aload 5
      // 0a3: bipush 0
      // 0a4: invokeinterface org/bukkit/configuration/ConfigurationSection.getKeys (Z)Ljava/util/Set; 2
      // 0a9: invokespecial java/util/ArrayList.<init> (Ljava/util/Collection;)V
      // 0ac: astore 6
      // 0ae: bipush 54
      // 0b0: bipush 27
      // 0b2: aload 6
      // 0b4: invokeinterface java/util/List.size ()I 1
      // 0b9: bipush 8
      // 0bb: iadd
      // 0bc: bipush 9
      // 0be: idiv
      // 0bf: bipush 9
      // 0c1: imul
      // 0c2: invokestatic java/lang/Math.max (II)I
      // 0c5: invokestatic java/lang/Math.min (II)I
      // 0c8: istore 7
      // 0ca: new com/sharded/core/modules/punishments/PunishmentsModule$PunishHolder
      // 0cd: dup
      // 0ce: getstatic com/sharded/core/modules/punishments/PunishmentsModule$GuiType.PUNISH_REASONS Lcom/sharded/core/modules/punishments/PunishmentsModule$GuiType;
      // 0d1: aload 2
      // 0d2: invokespecial com/sharded/core/modules/punishments/PunishmentsModule$PunishHolder.<init> (Lcom/sharded/core/modules/punishments/PunishmentsModule$GuiType;Ljava/util/UUID;)V
      // 0d5: astore 8
      // 0d7: aload 0
      // 0d8: getfield com/sharded/core/modules/punishments/PunishmentsModule.config Lorg/bukkit/configuration/file/YamlConfiguration;
      // 0db: ldc_w "punish.reason-title"
      // 0de: ldc_w "&8%type% | %player%"
      // 0e1: invokevirtual org/bukkit/configuration/file/YamlConfiguration.getString (Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
      // 0e4: bipush 4
      // 0e5: anewarray 38
      // 0e8: dup
      // 0e9: bipush 0
      // 0ea: ldc_w "%type%"
      // 0ed: aastore
      // 0ee: dup
      // 0ef: bipush 1
      // 0f0: aload 4
      // 0f2: getstatic java/util/Locale.ROOT Ljava/util/Locale;
      // 0f5: invokevirtual java/lang/String.toUpperCase (Ljava/util/Locale;)Ljava/lang/String;
      // 0f8: aastore
      // 0f9: dup
      // 0fa: bipush 2
      // 0fb: ldc_w "%player%"
      // 0fe: aastore
      // 0ff: dup
      // 100: bipush 3
      // 101: aload 3
      // 102: aastore
      // 103: invokestatic com/sharded/core/util/Text.apply (Ljava/lang/String;[Ljava/lang/String;)Ljava/lang/String;
      // 106: astore 9
      // 108: aload 8
      // 10a: iload 7
      // 10c: aload 9
      // 10e: invokestatic com/sharded/core/util/Text.c (Ljava/lang/String;)Lnet/kyori/adventure/text/Component;
      // 111: invokestatic org/bukkit/Bukkit.createInventory (Lorg/bukkit/inventory/InventoryHolder;ILnet/kyori/adventure/text/Component;)Lorg/bukkit/inventory/Inventory;
      // 114: astore 10
      // 116: aload 10
      // 118: aload 8
      // 11a: invokestatic com/sharded/core/util/TrackedInventories.track (Lorg/bukkit/inventory/Inventory;Ljava/lang/Object;)V
      // 11d: aload 0
      // 11e: aload 10
      // 120: invokevirtual com/sharded/core/modules/punishments/PunishmentsModule.fill (Lorg/bukkit/inventory/Inventory;)V
      // 123: bipush 0
      // 124: istore 11
      // 126: aload 6
      // 128: invokeinterface java/util/List.iterator ()Ljava/util/Iterator; 1
      // 12d: astore 12
      // 12f: aload 12
      // 131: invokeinterface java/util/Iterator.hasNext ()Z 1
      // 136: ifeq 173
      // 139: aload 12
      // 13b: invokeinterface java/util/Iterator.next ()Ljava/lang/Object; 1
      // 140: checkcast java/lang/String
      // 143: astore 13
      // 145: iload 11
      // 147: iload 7
      // 149: bipush 9
      // 14b: isub
      // 14c: if_icmplt 152
      // 14f: goto 173
      // 152: aload 10
      // 154: iload 11
      // 156: iinc 11 1
      // 159: aload 0
      // 15a: aload 13
      // 15c: aload 5
      // 15e: aload 13
      // 160: invokeinterface org/bukkit/configuration/ConfigurationSection.get (Ljava/lang/String;)Ljava/lang/Object; 2
      // 165: aload 3
      // 166: aload 4
      // 168: invokevirtual com/sharded/core/modules/punishments/PunishmentsModule.reasonItem (Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Lorg/bukkit/inventory/ItemStack;
      // 16b: invokeinterface org/bukkit/inventory/Inventory.setItem (ILorg/bukkit/inventory/ItemStack;)V 3
      // 170: goto 12f
      // 173: aload 10
      // 175: iload 7
      // 177: bipush 5
      // 178: isub
      // 179: aload 0
      // 17a: ldc_w "punish.back"
      // 17d: invokevirtual com/sharded/core/modules/punishments/PunishmentsModule.navItem (Ljava/lang/String;)Lorg/bukkit/inventory/ItemStack;
      // 180: invokeinterface org/bukkit/inventory/Inventory.setItem (ILorg/bukkit/inventory/ItemStack;)V 3
      // 185: aload 1
      // 186: aload 10
      // 188: invokeinterface org/bukkit/entity/Player.openInventory (Lorg/bukkit/inventory/Inventory;)Lorg/bukkit/inventory/InventoryView; 2
      // 18d: pop
      // 18e: aload 0
      // 18f: getfield com/sharded/core/modules/punishments/PunishmentsModule.sessions Ljava/util/Map;
      // 192: aload 1
      // 193: invokeinterface org/bukkit/entity/Player.getUniqueId ()Ljava/util/UUID; 1
      // 198: new com/sharded/core/modules/punishments/PunishmentsModule$GuiSession
      // 19b: dup
      // 19c: getstatic com/sharded/core/modules/punishments/PunishmentsModule$GuiType.PUNISH_REASONS Lcom/sharded/core/modules/punishments/PunishmentsModule$GuiType;
      // 19f: aload 2
      // 1a0: aload 4
      // 1a2: aconst_null
      // 1a3: aconst_null
      // 1a4: invokespecial com/sharded/core/modules/punishments/PunishmentsModule$GuiSession.<init> (Lcom/sharded/core/modules/punishments/PunishmentsModule$GuiType;Ljava/util/UUID;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
      // 1a7: invokeinterface java/util/Map.put (Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object; 3
      // 1ac: pop
      // 1ad: return
   }

   private ItemStack reasonItem(String reason, Object durations, String targetName, String type) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("punish.reason-item");
      Material material = Material.matchMaterial(configurationsection == null ? "PAPER" : configurationsection.getString("material", "PAPER"));
      if (material == null) {
         material = Material.PAPER;
      }

      List<String> list = this.formatDurationChoices(durations);
      String s = list.isEmpty() ? "Permanent" : String.join(", ", list);
      List<String> list1 = new ArrayList<>();

      for (String s1 : configurationsection == null ? List.of("&7Click to apply") : configurationsection.getStringList("lore")) {
         list1.add(
            s1.replace("%durations%", String.join("\n", list))
               .replace("%duration%", s)
               .replace("%reason%", reason)
               .replace("%player%", targetName)
               .replace("%type%", type)
         );
      }

      return new ItemBuilder(material)
         .name(
            (configurationsection == null ? "&#00FFAA%reason%" : configurationsection.getString("display_name", "&#00FFAA%reason%"))
               .replace("%reason%", reason)
         )
         .lore(list1)
         .hideAll()
         .build();
   }

   private List<String> formatDurationChoices(Object raw) {
      List<String> list = new ArrayList<>();
      if (raw instanceof List) {
         for (Object object : (List)raw) {
            list.add(String.valueOf(object));
         }
      } else if (raw != null) {
         list.add(String.valueOf(raw));
      }

      return list;
   }

   private String firstDuration(Object raw) {
      List<String> list = this.formatDurationChoices(raw);
      return list.isEmpty() ? "permanent" : list.get(0);
   }

   @EventHandler
   public void onGuiClick(InventoryClickEvent event) {
      if (event.getWhoClicked() instanceof Player player) {
         PunishmentsModule.PunishHolder punishmentsmodule$punishholder = TrackedInventories.lookup(
            event.getView().getTopInventory(), PunishmentsModule.PunishHolder.class
         );
         if (punishmentsmodule$punishholder != null) {
            event.setCancelled(true);
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
               PunishmentsModule.GuiSession punishmentsmodule$guisession = this.sessions.get(player.getUniqueId());
               if (punishmentsmodule$guisession != null) {
                  String s = OfflinePlayers.name(punishmentsmodule$punishholder.targetId());
                  ItemStack itemstack = event.getCurrentItem();
                  if (itemstack != null && itemstack.hasItemMeta()) {
                     if (punishmentsmodule$punishholder.type() == PunishmentsModule.GuiType.PUNISH_MAIN) {
                        int i = event.getSlot();
                        if (i == 11) {
                           this.openReasonMenu(player, punishmentsmodule$punishholder.targetId(), s, "ban");
                        } else if (i == 15) {
                           this.openReasonMenu(player, punishmentsmodule$punishholder.targetId(), s, "mute");
                        } else if (i == 16) {
                           this.openReasonMenu(player, punishmentsmodule$punishholder.targetId(), s, "ipban");
                        }
                     } else {
                        if (punishmentsmodule$punishholder.type() == PunishmentsModule.GuiType.PUNISH_REASONS) {
                           if (event.getSlot() == event.getView().getTopInventory().getSize() - 5) {
                              this.openPunishMain(player, punishmentsmodule$punishholder.targetId(), s);
                              return;
                           }

                           String s1 = itemstack.getItemMeta().hasDisplayName()
                              ? PlainTextComponentSerializer.plainText().serialize(itemstack.getItemMeta().displayName())
                              : null;
                           if (s1 == null || s1.isBlank()) {
                              return;
                           }

                           s1 = ColorUtil.normalize(s1).replaceAll("(?i)&#[0-9a-f]{6}|&[0-9a-fk-or]", "").trim();
                           ConfigurationSection configurationsection = "mute".equalsIgnoreCase(punishmentsmodule$guisession.punishType())
                              ? this.config.getConfigurationSection("mute-reasons")
                              : this.config.getConfigurationSection("reasons");
                           if (configurationsection == null || !configurationsection.contains(s1)) {
                              return;
                           }

                           String s2 = this.firstDuration(configurationsection.get(s1));
                           this.applyFromGui(player, punishmentsmodule$punishholder.targetId(), s, punishmentsmodule$guisession.punishType(), s1, s2);
                           player.closeInventory();
                        }

                        if (punishmentsmodule$punishholder.type() == PunishmentsModule.GuiType.WIPE_CONFIRM) {
                           if (event.getSlot() == this.config.getInt("wipe.items.confirm.slot", 15)) {
                              this.wipePlayer(player, punishmentsmodule$punishholder.targetId(), s, punishmentsmodule$guisession.reason());
                              player.closeInventory();
                           } else if (event.getSlot() == this.config.getInt("wipe.items.cancel.slot", 11)) {
                              player.closeInventory();
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private void applyFromGui(Player staff, UUID targetId, String targetName, String type, String reason, String duration) {
      OfflinePlayer offlineplayer = Bukkit.getOfflinePlayer(targetId);
      String s = type.toLowerCase(Locale.ROOT);
      switch (s) {
         case "ban":
            this.ban(staff, offlineplayer, reason, duration);
            break;
         case "mute":
            this.mute(staff, offlineplayer, reason, duration);
            break;
         case "ipban":
            this.banIp(staff, offlineplayer, reason, duration);
            break;
         default:
            this.send(staff, "unknown-punish-type", new String[0]);
      }
   }

   public void kick(CommandSender staff, Player target, String reason) {
      if (!this.can(staff, "sharded.punishments.kick")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         String s = staff.getName() == null ? "Console" : staff.getName();
         UUID uuid = staff instanceof Player player ? player.getUniqueId() : null;
         this.database.addPunishment(target.getUniqueId(), target.getName(), uuid, s, PunishmentDatabase.PunishmentType.KICK, reason, null, null, false);
         target.kick(this.buildKickScreen("kick-screen", s, reason));
         this.send(staff, "kicked", new String[]{"%player%", target.getName(), "%reason%", reason});
      }
   }

   public void ban(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
      if (!this.can(staff, "sharded.punishments.ban")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         boolean flag = this.isDoxxingReason(reason);
         if (flag) {
            durationRaw = "permanent";
         }

         Long olong = flag ? null : DurationUtil.expiresAt(durationRaw);
         if (!flag && olong != null && olong < 0L) {
            this.send(staff, "invalid-duration", new String[0]);
         } else {
            String s = staff.getName() == null ? "Console" : staff.getName();
            UUID uuid = staff instanceof Player player ? player.getUniqueId() : null;
            this.database.deactivatePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.BAN);
            String s1 = this.database.latestIp(target.getUniqueId());
            this.database
               .addPunishment(
                  target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), uuid, s, PunishmentDatabase.PunishmentType.BAN, reason, olong, s1, flag
               );
            if (flag) {
               this.database.markDoxxed(target.getUniqueId());
            }

            Player player1 = target.getPlayer();
            if (player1 != null) {
               if (flag) {
                  player1.kick(this.buildBanEvasionScreen("doxxing-deny-screen"));
               } else {
                  player1.kick(this.buildKickComponent("ban-screen", s, reason, olong));
               }
            }

            this.send(
               staff,
               "banned",
               new String[]{
                  "%player%", OfflinePlayers.name(target.getUniqueId()), "%reason%", reason, "%duration%", this.formatDurationLabel(durationRaw, olong)
               }
            );
            this.broadcastPunishment("Ban", target, s, reason);
            this.sendPunishmentWebhook("ban-webhook", target, s, reason, "Ban");
            if (flag) {
               this.sendDoxxingWebhook(target, s, reason);
            }
         }
      }
   }

   private boolean isDoxxingReason(String reason) {
      if (reason == null) {
         return false;
      } else {
         for (String s : this.config.getStringList("doxxing-reasons")) {
            if (reason.equalsIgnoreCase(s)) {
               return true;
            }
         }

         return false;
      }
   }

   private void sendPunishmentWebhook(String configPath, OfflinePlayer target, String staffName, String reason, String action) {
      if (this.config.getBoolean(configPath + ".enabled", false)) {
         String s = this.config.getString(configPath + ".url", "");
         if (!s.isBlank()) {
            String s1 = OfflinePlayers.name(target.getUniqueId());
            String s2 = this.config
               .getString(configPath + ".thumbnail-url", "https://mc-heads.net/avatar/%uuid%")
               .replace("%uuid%", target.getUniqueId().toString())
               .replace("%player%", s1);
            List<DiscordWebhook.Field> list = List.of(
               new DiscordWebhook.Field("Player", s1, true),
               new DiscordWebhook.Field("Staff", staffName, true),
               new DiscordWebhook.Field("Reason", reason, false)
            );
            DiscordWebhook.sendEmbedAsync(
               this.plugin.getLogger(),
               s,
               this.config.getString(configPath + ".title", action),
               this.config
                  .getString(configPath + ".description", "%player% was %action% by %staff% for %reason%.")
                  .replace("%player%", s1)
                  .replace("%staff%", staffName)
                  .replace("%reason%", reason)
                  .replace("%action%", action.toLowerCase(Locale.ROOT)),
               (int)this.config.getLong(configPath + ".color", 16711680L),
               s2,
               this.config.getString(configPath + ".footer", "ShardedCore Punishments"),
               list
            );
         }
      }
   }

   private void sendDoxxingWebhook(OfflinePlayer target, String staffName, String reason) {
      if (this.config.getBoolean("doxxing-webhook.enabled", false)) {
         String s = this.config.getString("doxxing-webhook.url", "");
         if (!s.isBlank()) {
            String s1 = OfflinePlayers.name(target.getUniqueId());
            String s2 = this.config
               .getString("doxxing-webhook.thumbnail-url", "https://mc-heads.net/avatar/%uuid%")
               .replace("%uuid%", target.getUniqueId().toString())
               .replace("%player%", s1);
            String s3 = this.config.getString("doxxing-webhook.reban-command", "/ban %player% Doxxing permanent").replace("%player%", s1);
            List<DiscordWebhook.Field> list = List.of(
               new DiscordWebhook.Field("Player", s1, true),
               new DiscordWebhook.Field("UUID", target.getUniqueId().toString(), true),
               new DiscordWebhook.Field("Staff", staffName, true),
               new DiscordWebhook.Field("Reason", reason, false),
               new DiscordWebhook.Field("Re-ban", s3, false)
            );
            DiscordWebhook.sendEmbedAsync(
               this.plugin.getLogger(),
               s,
               this.config.getString("doxxing-webhook.title", "Doxxing Ban"),
               this.config
                  .getString("doxxing-webhook.description", "%player% was permanently banned for doxxing.")
                  .replace("%player%", s1)
                  .replace("%staff%", staffName)
                  .replace("%reason%", reason),
               (int)this.config.getLong("doxxing-webhook.color", 16711680L),
               s2,
               this.config.getString("doxxing-webhook.footer", "ShardedCore Punishments"),
               list
            );
         }
      }
   }

   public void mute(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
      if (!this.can(staff, "sharded.punishments.mute")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         Long olong = DurationUtil.expiresAt(durationRaw);
         if (olong != null && olong < 0L) {
            this.send(staff, "invalid-duration", new String[0]);
         } else {
            String s = staff.getName() == null ? "Console" : staff.getName();
            UUID uuid = staff instanceof Player player ? player.getUniqueId() : null;
            this.database.deactivatePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
            this.database
               .addPunishment(
                  target.getUniqueId(), OfflinePlayers.name(target.getUniqueId()), uuid, s, PunishmentDatabase.PunishmentType.MUTE, reason, olong, null, false
               );
            this.invalidateMuteCache(target.getUniqueId());
            this.send(
               staff,
               "muted",
               new String[]{
                  "%player%", OfflinePlayers.name(target.getUniqueId()), "%reason%", reason, "%duration%", this.formatDurationLabel(durationRaw, olong)
               }
            );
            Player player1 = target.getPlayer();
            if (player1 != null) {
               this.showMuteScreen(player1, s, reason, olong);
            }

            this.broadcastPunishment("Mute", target, s, reason);
            this.sendPunishmentWebhook("mute-webhook", target, s, reason, "Mute");
         }
      }
   }

   public void banIp(CommandSender staff, OfflinePlayer target, String reason, String durationRaw) {
      if (!this.can(staff, "sharded.punishments.banip")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         String s = target.isOnline() && target.getPlayer() != null
            ? target.getPlayer().getAddress().getAddress().getHostAddress()
            : this.database.latestIp(target.getUniqueId());
         if (s != null && !s.isBlank()) {
            Long olong = DurationUtil.expiresAt(durationRaw);
            if (olong != null && olong < 0L) {
               this.send(staff, "invalid-duration", new String[0]);
            } else {
               String s1 = staff.getName() == null ? "Console" : staff.getName();
               this.database.addIpBan(s, reason, s1, olong);
               this.database
                  .addPunishment(
                     target.getUniqueId(),
                     OfflinePlayers.name(target.getUniqueId()),
                     staff instanceof Player player ? player.getUniqueId() : null,
                     s1,
                     PunishmentDatabase.PunishmentType.IP_BAN,
                     reason,
                     olong,
                     s,
                     false
                  );

               for (Player player1 : Bukkit.getOnlinePlayers()) {
                  String s2 = player1.getAddress() == null ? null : player1.getAddress().getAddress().getHostAddress();
                  if (s.equals(s2)) {
                     player1.kick(this.buildKickComponent("ban-screen", s1, reason, olong));
                  }
               }

               this.send(staff, "ip-banned", new String[]{"%player%", OfflinePlayers.name(target.getUniqueId()), "%reason%", reason});
            }
         } else {
            this.send(staff, "no-ip", new String[0]);
         }
      }
   }

   public void unban(CommandSender staff, OfflinePlayer target) {
      if (!this.can(staff, "sharded.punishments.unban")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         String s = OfflinePlayers.name(target.getUniqueId());
         List<String> list = this.database.ipsForPlayer(target.getUniqueId());
         this.database.clearAllBansForPlayer(target.getUniqueId());
         this.pardonVanillaForPlayer(s, list);
         this.send(staff, "unbanned", new String[]{"%player%", s});
      }
   }

   public void clearAllBansForPlayer(CommandSender staff, OfflinePlayer target) {
      if (!this.can(staff, "sharded.punishments.unbanip") && !this.can(staff, "sharded.punishments.unban")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         String s = OfflinePlayers.name(target.getUniqueId());
         List<String> list = this.database.ipsForPlayer(target.getUniqueId());
         this.database.clearAllBansForPlayer(target.getUniqueId());
         this.pardonVanillaForPlayer(s, list);
         this.send(staff, "unbanip-player-cleared", new String[]{"%player%", s});
      }
   }

   public void unbanIp(CommandSender staff, String ip) {
      if (!this.can(staff, "sharded.punishments.unbanip")) {
         this.send(staff, "no-permission", new String[0]);
      } else if (ip != null && !ip.isBlank()) {
         boolean flag = this.database.hasAnyIpBlock(ip);
         boolean flag1 = VanillaBanHelper.isIpBanned(ip);
         this.database.clearIpBlock(ip);
         VanillaBanHelper.pardonIp(ip);
         if (!flag && !flag1) {
            this.send(staff, "unbanip-not-found", new String[]{"%ip%", ip});
         } else {
            this.send(staff, "unbanip-done", new String[]{"%ip%", ip});
         }
      } else {
         this.send(staff, "unbanip-usage", new String[0]);
      }
   }

   public void unmute(CommandSender staff, OfflinePlayer target) {
      if (!this.can(staff, "sharded.punishments.unmute")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         this.database.deactivatePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
         this.invalidateMuteCache(target.getUniqueId());
         this.send(staff, "unmuted", new String[]{"%player%", OfflinePlayers.name(target.getUniqueId())});
      }
   }

   public void pardon(CommandSender staff, OfflinePlayer target) {
      if (!this.can(staff, "sharded.punishments.pardon")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         this.unban(staff, target);
         this.unmute(staff, target);
         this.send(staff, "pardoned", new String[]{"%player%", OfflinePlayers.name(target.getUniqueId())});
      }
   }

   public void showOffenses(CommandSender sender, OfflinePlayer target) {
      if (!sender.hasPermission("sharded.punishments.offend")) {
         this.send(sender, "no-permission", new String[0]);
      } else {
         int i = this.database.countActivePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.BAN);
         int j = this.database.countActivePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.MUTE);
         int k = this.database.countActivePunishments(target.getUniqueId(), PunishmentDatabase.PunishmentType.WARN);
         this.send(
            sender,
            "offenses",
            new String[]{
               "%player%", OfflinePlayers.name(target.getUniqueId()), "%bans%", String.valueOf(i), "%mutes%", String.valueOf(j), "%warns%", String.valueOf(k)
            }
         );
         int l = this.config.getInt("repeat-offender-threshold", 3);
         if (i >= l) {
            this.send(sender, "repeat-offender", new String[]{"%player%", OfflinePlayers.name(target.getUniqueId())});
         }
      }
   }

   public void showAlts(CommandSender sender, OfflinePlayer target) {
      if (!this.can(sender, "sharded.punishments.alts")) {
         this.send(sender, "no-permission", new String[0]);
      } else {
         String s = target.isOnline() && target.getPlayer() != null
            ? target.getPlayer().getAddress().getAddress().getHostAddress()
            : this.database.latestIp(target.getUniqueId());
         if (s == null) {
            this.send(sender, "no-ip", new String[0]);
         } else {
            List<PunishmentDatabase.AltAccount> list = this.database.findAlts(s, target.getUniqueId());
            String s1 = OfflinePlayers.name(target.getUniqueId());
            this.sendRaw(sender, this.altMessage("header", "%player%", s1));
            if (list.isEmpty()) {
               this.sendRaw(sender, this.altMessage("none"));
            } else {
               int i = this.config.getInt("alts.max-display", 50);

               for (int j = 0; j < Math.min(i, list.size()); j++) {
                  PunishmentDatabase.AltAccount punishmentdatabase$altaccount = list.get(j);
                  boolean flag = Bukkit.getPlayer(punishmentdatabase$altaccount.uuid()) != null;
                  this.sendRaw(
                     sender,
                     this.altMessage(
                        "entry", "%name%", punishmentdatabase$altaccount.name(), "%status%", flag ? this.altMessage("online") : this.altMessage("offline")
                     )
                  );
               }
            }
         }
      }
   }

   private String altMessage(String key, String... replacements) {
      String s = "alts.chat." + key;
      String s1 = this.config.getString(s);
      if (s1 == null || s1.isBlank()) {
         if ("header".equals(key)) {
            s1 = this.messages.getString("alts-header");
         } else if ("none".equals(key)) {
            s1 = this.messages.getString("alts-none");
         } else if ("entry".equals(key)) {
            s1 = this.messages.getString("alts-entry");
         } else if ("online".equals(key)) {
            s1 = this.messages.getString("alts-online");
         } else if ("offline".equals(key)) {
            s1 = this.messages.getString("alts-offline");
         }
      }

      if (s1 == null) {
         s1 = "";
      }

      s1 = s1.replace("%prefix%", this.messagePrefix());
      return Text.apply(s1, replacements);
   }

   private void sendRaw(CommandSender to, String msg) {
      if (msg != null && !msg.isEmpty()) {
         MessageUtil.deliver(to, Text.c(msg), this.resolveDelivery("alts"));
      }
   }

   public void openWipeConfirm(Player staff, OfflinePlayer target, String reasonKey) {
      if (!this.can(staff, "sharded.punishments.wipe")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         int i = this.config.getInt("wipe.rows", 3);
         PunishmentsModule.PunishHolder punishmentsmodule$punishholder = new PunishmentsModule.PunishHolder(
            PunishmentsModule.GuiType.WIPE_CONFIRM, target.getUniqueId()
         );
         String s = Text.apply(this.config.getString("wipe.title", "&8Wipe | %player%"), "%player%", OfflinePlayers.name(target.getUniqueId()));
         Inventory inventory = Bukkit.createInventory(punishmentsmodule$punishholder, i * 9, Text.c(s));
         TrackedInventories.track(inventory, punishmentsmodule$punishholder);
         this.fillWipe(inventory, OfflinePlayers.name(target.getUniqueId()));
         staff.openInventory(inventory);
         this.sessions
            .put(staff.getUniqueId(), new PunishmentsModule.GuiSession(PunishmentsModule.GuiType.WIPE_CONFIRM, target.getUniqueId(), null, reasonKey, null));
      }
   }

   public void wipePlayer(CommandSender staff, UUID targetId, String targetName, String reasonKey) {
      if (!this.can(staff, "sharded.punishments.wipe")) {
         this.send(staff, "no-permission", new String[0]);
      } else {
         Player player = Bukkit.getPlayer(targetId);
         if (player != null) {
            player.getInventory().clear();
            player.getEnderChest().clear();
            player.setLevel(0);
            player.setExp(0.0F);
            player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));

            for (Statistic statistic : Statistic.values()) {
               try {
                  player.setStatistic(statistic, 0);
               } catch (Exception exception) {
               }
            }

            SpawnSelectModule spawnselectmodule = this.plugin.modules().get(SpawnSelectModule.class);
            if (spawnselectmodule != null) {
               Location location = spawnselectmodule.mainSpawn();
               if (location != null) {
                  player.teleport(location);
               }
            }

            List<String> list = this.wipeKickScreen(reasonKey);
            player.kick(this.buildLines(list, targetName, staff.getName(), reasonKey));
         }

         this.plugin.stateStore().clear(targetId);
         if (this.plugin.modules().tokens() != null) {
            this.plugin.modules().tokens().reset(targetId);
         }

         this.killstreakReset(targetId);
         this.send(staff, "wiped", new String[]{"%player%", targetName, "%reason%", reasonKey == null ? "default" : reasonKey});
      }
   }

   private void killstreakReset(UUID uuid) {
      KillstreaksModule killstreaksmodule = this.plugin.modules().get(KillstreaksModule.class);
      if (killstreaksmodule != null && killstreaksmodule.database() != null) {
         killstreaksmodule.database().reset(uuid);
      }
   }

   private List<String> wipeKickScreen(String reasonKey) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("wipe.reasons");
      return reasonKey != null && configurationsection != null && configurationsection.isList(reasonKey)
         ? configurationsection.getStringList(reasonKey)
         : this.config.getStringList("wipe.kick-screen");
   }

   private Component buildLines(List<String> lines, String player, String staff, String reason) {
      List<Component> list = new ArrayList<>();

      for (String s : lines) {
         list.add(Text.c(Text.apply(s, "%player%", player, "%staff%", staff == null ? "Staff" : staff, "%reason%", reason == null ? "" : reason)));
      }

      return Component.join(JoinConfiguration.newlines(), list);
   }

   private Component buildKickComponent(String configKey, String staff, String reason, Long expiresAt) {
      List<String> list = !DurationUtil.isPermanent(String.valueOf(expiresAt)) && expiresAt != null
         ? this.config.getStringList(configKey)
         : this.config.getStringList("ban-screen-permanent");
      if (list.isEmpty()) {
         list = this.config.getStringList(configKey);
      }

      List<Component> list1 = new ArrayList<>();

      for (String s : list) {
         list1.add(
            Text.c(
               Text.apply(
                  s,
                  "%staff%",
                  staff,
                  "%reason%",
                  reason,
                  "%time_left%",
                  expiresAt == null ? "Permanent" : DurationUtil.formatRemaining(expiresAt),
                  "%expires%",
                  expiresAt == null ? "Never" : DurationUtil.formatExpires(expiresAt),
                  "%discord%",
                  this.config.getString("discord", "discord.gg/shardedmc")
               )
            )
         );
      }

      return Component.join(JoinConfiguration.newlines(), list1);
   }

   private Component buildKickScreen(String configKey, String staff, String reason) {
      List<String> list = this.config.getStringList(configKey);
      List<Component> list1 = new ArrayList<>();

      for (String s : list) {
         list1.add(Text.c(Text.apply(s, "%staff%", staff, "%reason%", reason, "%discord%", this.config.getString("discord", ".gg/shardedmc"))));
      }

      return Component.join(JoinConfiguration.newlines(), list1);
   }

   private void showMuteScreen(Player target, String staff, String reason, Long expiresAt) {
      Component component = this.buildMuteScreen(staff, reason, expiresAt);
      target.sendMessage(component);
      if (this.config.getBoolean("mute-screen-title.enabled", true)) {
         target.showTitle(
            Title.title(
               Text.c(this.config.getString("mute-screen-title.title", "&#FF2727&lMUTED")),
               Text.c(
                  Text.apply(
                     this.config.getString("mute-screen-title.subtitle", "&f%reason%"),
                     "%reason%",
                     reason,
                     "%staff%",
                     staff,
                     "%time_left%",
                     expiresAt == null ? "Permanent" : DurationUtil.formatRemaining(expiresAt),
                     "%expires%",
                     expiresAt == null ? "Never" : DurationUtil.formatExpires(expiresAt)
                  )
               ),
               Times.times(Duration.ofMillis(250L), Duration.ofMillis(3500L), Duration.ofMillis(500L))
            )
         );
      }
   }

   private Component buildMuteScreen(String staff, String reason, Long expiresAt) {
      boolean flag = expiresAt == null || DurationUtil.isPermanent(String.valueOf(expiresAt));
      List<String> list = flag ? this.config.getStringList("mute-screen-permanent") : this.config.getStringList("mute-screen");
      if (list.isEmpty()) {
         list = List.of(
            "&#AD4EFF&lSHARDEDMC",
            "&cYou have been muted!",
            "",
            "&#AD4EFF⛨&r &fBy: &#AD4EFF%staff%",
            "&#FF007B⚐&r &fReason: &#FF007B%reason%",
            "&#45FF17☄&r &fDuration: &#45FF17%time_left%"
         );
      }

      List<Component> list1 = new ArrayList<>();

      for (String s : list) {
         list1.add(
            Text.c(
               Text.apply(
                  s,
                  "%staff%",
                  staff,
                  "%reason%",
                  reason,
                  "%time_left%",
                  expiresAt == null ? "Permanent" : DurationUtil.formatRemaining(expiresAt),
                  "%expires%",
                  expiresAt == null ? "Never" : DurationUtil.formatExpires(expiresAt),
                  "%discord%",
                  this.config.getString("discord", "discord.gg/shardedmc")
               )
            )
         );
      }

      return Component.join(JoinConfiguration.newlines(), list1);
   }

   @EventHandler(
      priority = EventPriority.HIGHEST
   )
   public void onPreLogin(AsyncPlayerPreLoginEvent event) {
      if (this.database.isDoxxed(event.getUniqueId())) {
         event.disallow(Result.KICK_BANNED, this.buildBanEvasionScreen("doxxing-deny-screen"));
      } else {
         InetAddress inetaddress = event.getAddress();
         String s = inetaddress == null ? null : inetaddress.getHostAddress();
         if (s != null) {
            PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord = this.database.getActiveIpBan(s);
            if (punishmentdatabase$punishmentrecord != null) {
               event.disallow(
                  Result.KICK_BANNED,
                  this.buildKickComponent(
                     "ban-screen",
                     punishmentdatabase$punishmentrecord.staffName(),
                     punishmentdatabase$punishmentrecord.reason(),
                     punishmentdatabase$punishmentrecord.expiresAt()
                  )
               );
               return;
            }

            if (this.config.getBoolean("ban-evasion.enabled", true) && this.database.hasBannedAltOnIp(s, event.getUniqueId())) {
               event.disallow(Result.KICK_BANNED, this.buildBanEvasionScreen("ban-evasion-screen"));
               return;
            }
         }

         PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord1 = this.database
            .getActive(event.getUniqueId(), PunishmentDatabase.PunishmentType.BAN);
         if (punishmentdatabase$punishmentrecord1 != null) {
            event.disallow(
               Result.KICK_BANNED,
               this.buildKickComponent(
                  "ban-screen",
                  punishmentdatabase$punishmentrecord1.staffName(),
                  punishmentdatabase$punishmentrecord1.reason(),
                  punishmentdatabase$punishmentrecord1.expiresAt()
               )
            );
         }
      }
   }

   private Component buildBanEvasionScreen(String key) {
      List<String> list = this.config.getStringList(key);
      List<Component> list1 = new ArrayList<>();

      for (String s : list) {
         list1.add(Text.c(Text.apply(s, "%discord%", this.config.getString("discord", ".gg/shardedmc"))));
      }

      return Component.join(JoinConfiguration.newlines(), list1);
   }

   @EventHandler
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      if (player.getAddress() != null) {
         UUID uuid = player.getUniqueId();
         String s = player.getName();
         String s1 = player.getAddress().getAddress().getHostAddress();
         this.plugin.getServer().getScheduler().runTaskAsynchronously(this.plugin, () -> this.database.recordIp(uuid, s, s1));
      }
   }

   private PunishmentDatabase.PunishmentRecord getActiveMute(UUID uuid) {
      long i = System.currentTimeMillis();
      PunishmentsModule.CachedMute punishmentsmodule$cachedmute = this.muteCache.get(uuid);
      if (punishmentsmodule$cachedmute != null && i < punishmentsmodule$cachedmute.cacheUntilMs()) {
         return punishmentsmodule$cachedmute.record();
      } else {
         PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord = this.database.getActive(uuid, PunishmentDatabase.PunishmentType.MUTE);
         long j = punishmentdatabase$punishmentrecord == null
            ? i + 10000L
            : (
               punishmentdatabase$punishmentrecord.expiresAt() != null && punishmentdatabase$punishmentrecord.expiresAt() > 0L
                  ? Math.min(punishmentdatabase$punishmentrecord.expiresAt(), i + 60000L)
                  : Long.MAX_VALUE
            );
         this.muteCache.put(uuid, new PunishmentsModule.CachedMute(punishmentdatabase$punishmentrecord, j));
         return punishmentdatabase$punishmentrecord;
      }
   }

   private void invalidateMuteCache(UUID uuid) {
      this.muteCache.remove(uuid);
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onMutedCommand(PlayerCommandPreprocessEvent event) {
      PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord = this.getActiveMute(event.getPlayer().getUniqueId());
      if (punishmentdatabase$punishmentrecord != null) {
         String s = event.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
         int i = s.indexOf(58);
         if (i >= 0) {
            s = s.substring(i + 1);
         }

         for (String s1 : this.config.getStringList("mute.blocked-commands")) {
            if (s.equalsIgnoreCase(s1)) {
               event.setCancelled(true);
               this.send(event.getPlayer(), "muted-command", new String[]{"%command%", s});
               return;
            }
         }
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onChat(AsyncChatEvent event) {
      PunishmentDatabase.PunishmentRecord punishmentdatabase$punishmentrecord = this.getActiveMute(event.getPlayer().getUniqueId());
      if (punishmentdatabase$punishmentrecord != null) {
         event.setCancelled(true);
         this.plugin
            .getServer()
            .getScheduler()
            .runTask(
               this.plugin,
               () -> this.showMuteScreen(
                     event.getPlayer(),
                     punishmentdatabase$punishmentrecord.staffName(),
                     punishmentdatabase$punishmentrecord.reason(),
                     punishmentdatabase$punishmentrecord.expiresAt()
                  )
            );
      }
   }

   private void broadcastPunishment(String action, OfflinePlayer target, String staff, String reason) {
      if (this.config.getBoolean("public-broadcast.enabled", true)) {
         List<String> list = this.config.getStringList("public-broadcast.actions");
         if (list.contains(action)) {
            String s = ColorUtil.normalize(this.config.getString("punishment-prefix", "&#FF0000&lPUNISHMENTS &8▷ &r"));
            String s1 = this.messages.getString("broadcast-punish", "%prefix%&#FF2727%action% &f%player% &7by &f%staff% &7— &f%reason%");
            s1 = s1.replace("%prefix%", s)
               .replace("%action%", action)
               .replace("%player%", OfflinePlayers.name(target.getUniqueId()))
               .replace("%staff%", staff)
               .replace("%reason%", reason);
            Bukkit.broadcast(Text.c(s1));
         }
      }
   }

   private String formatDurationLabel(String raw, Long expiresAt) {
      if (expiresAt == null) {
         return "Permanent";
      } else {
         return DurationUtil.isPermanent(raw) ? "Permanent" : DurationUtil.formatRemaining(expiresAt);
      }
   }

   private void fill(Inventory inv) {
      if (this.config.getBoolean("punish.menu.filler.enabled", true)) {
         Material material = Material.matchMaterial(this.config.getString("punish.menu.filler.material", "GRAY_STAINED_GLASS_PANE"));
         if (material == null) {
            material = Material.GRAY_STAINED_GLASS_PANE;
         }

         ItemStack itemstack = new ItemBuilder(material).name(" ").hideAll().build();

         for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, itemstack);
         }
      }
   }

   private void fillWipe(Inventory inv, String targetName) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("wipe");
      if (configurationsection != null) {
         if (configurationsection.getBoolean("filler.enabled", true)) {
            Material material = Material.matchMaterial(configurationsection.getString("filler.material", "GRAY_STAINED_GLASS_PANE"));
            if (material == null) {
               material = Material.GRAY_STAINED_GLASS_PANE;
            }

            ItemStack itemstack = new ItemBuilder(material).name(" ").hideAll().build();

            for (int i = 0; i < inv.getSize(); i++) {
               inv.setItem(i, itemstack);
            }
         }

         this.putWipeItem(inv, "cancel", targetName);
         this.putWipeItem(inv, "info", targetName);
         this.putWipeItem(inv, "confirm", targetName);
      }
   }

   private void putWipeItem(Inventory inv, String key, String targetName) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("wipe.items." + key);
      if (configurationsection != null) {
         Material material = Material.matchMaterial(configurationsection.getString("material", "PAPER"));
         if (material == null) {
            material = Material.PAPER;
         }

         List<String> list = new ArrayList<>();

         for (String s : configurationsection.getStringList("lore")) {
            list.add(s.replace("%player%", targetName));
         }

         ItemStack itemstack = new ItemBuilder(material)
            .name(configurationsection.getString("display_name", key).replace("%player%", targetName))
            .lore(list)
            .hideAll()
            .build();
         if ("info".equals(key) && itemstack.getItemMeta() instanceof SkullMeta skullmeta) {
            OfflinePlayer offlineplayer = Bukkit.getOfflinePlayer(targetName);
            skullmeta.setOwningPlayer(offlineplayer);
            itemstack.setItemMeta(skullmeta);
         }

         inv.setItem(configurationsection.getInt("slot", 0), itemstack);
      }
   }

   private ItemStack menuItem(String path, String targetName, String type) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection(path);
      Material material = Material.matchMaterial(configurationsection == null ? "PAPER" : configurationsection.getString("material", "PAPER"));
      if (material == null) {
         material = Material.PAPER;
      }

      List<String> list = new ArrayList<>();
      if (configurationsection != null) {
         for (String s : configurationsection.getStringList("lore")) {
            list.add(s.replace("%player%", targetName));
         }
      }

      return new ItemBuilder(material)
         .name(configurationsection == null ? type : configurationsection.getString("display_name", type).replace("%player%", targetName))
         .lore(list)
         .hideAll()
         .build();
   }

   private ItemStack head(String playerName) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("punish.menu.target");
      Material material = Material.PLAYER_HEAD;
      ItemStack itemstack = new ItemBuilder(material)
         .name(configurationsection == null ? playerName : configurationsection.getString("display_name", playerName).replace("%player%", playerName))
         .lore(configurationsection == null ? List.of() : configurationsection.getStringList("lore"))
         .hideAll()
         .build();
      if (itemstack.getItemMeta() instanceof SkullMeta skullmeta) {
         skullmeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
         itemstack.setItemMeta(skullmeta);
      }

      return itemstack;
   }

   private ItemStack navItem(String path) {
      ConfigurationSection configurationsection = this.config.getConfigurationSection(path);
      Material material = Material.matchMaterial(configurationsection == null ? "BARRIER" : configurationsection.getString("material", "BARRIER"));
      if (material == null) {
         material = Material.BARRIER;
      }

      return new ItemBuilder(material)
         .name(configurationsection == null ? "Back" : configurationsection.getString("display_name", "Back"))
         .lore(configurationsection == null ? List.of() : configurationsection.getStringList("lore"))
         .hideAll()
         .build();
   }

   public List<String> banReasons() {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("reasons");
      return (List<String>)(configurationsection == null ? List.of() : new ArrayList<>(configurationsection.getKeys(false)));
   }

   public List<String> muteReasons() {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("mute-reasons");
      return (List<String>)(configurationsection == null ? List.of() : new ArrayList<>(configurationsection.getKeys(false)));
   }

   public List<String> wipeReasons() {
      ConfigurationSection configurationsection = this.config.getConfigurationSection("wipe.reasons");
      return (List<String>)(configurationsection == null ? List.of("default") : new ArrayList<>(configurationsection.getKeys(false)));
   }

   private static record CachedMute(PunishmentDatabase.PunishmentRecord record, long cacheUntilMs) {
   }

   private static record GuiSession(PunishmentsModule.GuiType type, UUID target, String punishType, String reason, String duration) {
   }

   private static enum GuiType {
      PUNISH_MAIN,
      PUNISH_REASONS,
      WIPE_CONFIRM;
   }

   private static final class PunishHolder implements InventoryHolder {
      private final PunishmentsModule.GuiType type;
      private final UUID targetId;

      PunishHolder(PunishmentsModule.GuiType type, UUID targetId) {
         this.type = type;
         this.targetId = targetId;
      }

      PunishmentsModule.GuiType type() {
         return this.type;
      }

      UUID targetId() {
         return this.targetId;
      }

      public Inventory getInventory() {
         return null;
      }
   }
}

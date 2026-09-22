package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.punishments.PunishmentDatabase;
import com.sharded.core.modules.punishments.PunishmentsModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class CoreTabComplete implements TabCompleter {
   private final ShardedCore plugin;

   public CoreTabComplete(ShardedCore plugin) {
      this.plugin = plugin;
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (command == null) {
         return List.of();
      } else {
         String s = command.getName().toLowerCase(Locale.ROOT);
         if (args.length == 1) {
            return (List<String>)(switch (s) {
               case "backpack", "punish", "ban", "banip", "kick", "mute", "offend", "wipe", "invrollback", "screenshare", "freeze" -> TabCompleteHelper.knownPlayers(
               args[0]
            );
               case "fly" -> {
                  List<String> list = new ArrayList<>(TabCompleteHelper.onlinePlayers(args[0]));
                  list.addAll(TabCompleteHelper.filter(args[0], "speed", "pos1", "pos2", "setregion"));
                  yield list;
               }
               case "alts" -> TabCompleteHelper.knownPlayers(args[0]);
               case "spawn", "setspawn" -> TabCompleteHelper.filter(args[0], "main", "vanilla");
               case "temprank", "rankshop", "temprankshop" -> TabCompleteHelper.filter(args[0], "shop");
               case "tokens" -> TabCompleteHelper.filter(args[0], "give", "set", "remove", "take", "reset", "giveall");
               case "pet" -> TabCompleteHelper.filter(args[0], "equip", "remove", "rename");
               case "eglow", "glows", "glowing" -> TabCompleteHelper.filter(args[0], "off");
               case "namecolor", "namecolors" -> TabCompleteHelper.filter(args[0], "custom");
               case "tag", "tags" -> TabCompleteHelper.filter(args[0], "custom");
               case "chattoggle", "msgtoggle", "deathtoggle", "jointoggle" -> TabCompleteHelper.filter(args[0], "toggle");
               case "killstreak" -> TabCompleteHelper.filter(args[0], "best", "player");
               case "graves" -> TabCompleteHelper.filter(args[0], "reload", "clear", "list");
               default -> List.of();
            });
         } else if (args.length == 2) {
            return switch (s) {
               case "invrollback" -> List.of();
               case "msg", "tell", "whisper", "w", "pm" -> TabCompleteHelper.onlinePlayers(args[1]);
               case "backpack" -> TabCompleteHelper.onlinePlayers(args[1]);
               case "fly" -> args[0].equalsIgnoreCase("speed")
               ? TabCompleteHelper.filter(args[1], "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
               : TabCompleteHelper.onlinePlayers(args[1]);
               case "tokens" -> {
                  String s2 = args[0].toLowerCase(Locale.ROOT);
                  switch (s2) {
                     case "give":
                     case "set":
                     case "remove":
                     case "take":
                     case "reset":
                        yield TabCompleteHelper.onlinePlayers(args[1]);
                     case "giveall":
                        yield TabCompleteHelper.filter(args[1], "100", "500", "1000", "5000");
                     default:
                        yield List.of();
                  }
               }
               case "setspawn" -> this.worldNames(args[1]);
               case "pet" -> {
                  String s1 = args[0].toLowerCase(Locale.ROOT);
                  switch (s1) {
                     case "equip":
                     case "rename":
                        yield TabCompleteHelper.filter(args[1], "dog", "cat", "parrot", "axolotl", "allay");
                     default:
                        yield List.of();
                  }
               }
               case "killstreak" -> args[0].equalsIgnoreCase("player") ? TabCompleteHelper.knownPlayers(args[1]) : List.of();
               default -> this.punishmentsSecondArg(s, args);
            };
         } else {
            if (args.length == 3) {
               PunishmentsModule punishmentsmodule = this.plugin.modules().get(PunishmentsModule.class);
               if (punishmentsmodule != null) {
                  return switch (s) {
                     case "ban", "banip", "offend" -> TabCompleteHelper.configKeys(args[2], punishmentsmodule.banReasons());
                     case "mute" -> TabCompleteHelper.configKeys(args[2], punishmentsmodule.muteReasons());
                     case "kick" -> TabCompleteHelper.configKeys(args[2], punishmentsmodule.kickReasons());
                     case "wipe" -> TabCompleteHelper.configKeys(args[2], punishmentsmodule.wipeReasons());
                     default -> List.of();
                  };
               }
            }

            return List.of();
         }
      }
   }

   private List<String> punishmentsSecondArg(String name, String[] args) {
      PunishmentsModule punishmentsmodule = this.plugin.modules().get(PunishmentsModule.class);
      if (punishmentsmodule == null) {
         return List.of();
      } else {
         return switch (name) {
            case "ban", "banip", "offend" -> TabCompleteHelper.configKeys(args[1], punishmentsmodule.banReasons());
            case "mute" -> TabCompleteHelper.configKeys(args[1], punishmentsmodule.muteReasons());
            case "kick" -> TabCompleteHelper.configKeys(args[1], punishmentsmodule.kickReasons());
            case "wipe" -> TabCompleteHelper.configKeys(args[1], punishmentsmodule.wipeReasons());
            case "unban" -> TabCompleteHelper.filter(args[1], punishmentsmodule.database().activePunishedPlayerNames(PunishmentDatabase.PunishmentType.BAN));
            case "unbanip" -> TabCompleteHelper.filter(args[1], punishmentsmodule.database().activeIpBans());
            case "unmute" -> TabCompleteHelper.filter(args[1], punishmentsmodule.database().activePunishedPlayerNames(PunishmentDatabase.PunishmentType.MUTE));
            default -> List.of();
         };
      }
   }

   private List<String> worldNames(String input) {
      List<String> list = new ArrayList<>();

      for (World world : Bukkit.getWorlds()) {
         list.add(world.getName());
      }

      return TabCompleteHelper.filter(input, list);
   }
}

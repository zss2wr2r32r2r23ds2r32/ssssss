package com.sharded.core.util;

import com.sharded.core.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;

public final class CommandHelp {
   private CommandHelp() {
   }

   public static List<CommandHelp.CommandInfo> all() {
      List<CommandHelp.CommandInfo> list = new ArrayList<>();
      list.add(new CommandHelp.CommandInfo("/craft", "Portable crafting table", "sharded.craft.use"));
      list.add(new CommandHelp.CommandInfo("/fix", "Repair held item", "sharded.fix.use"));
      list.add(new CommandHelp.CommandInfo("/trash", "Open trash bin", "sharded.trash.use"));
      list.add(new CommandHelp.CommandInfo("/chattoggle", "Toggle public chat", "sharded.chat.toggle"));
      list.add(new CommandHelp.CommandInfo("/msg <player> <msg>", "Private message", "sharded.msg.use"));
      list.add(new CommandHelp.CommandInfo("/reply <msg>", "Reply to last PM", "sharded.msg.use"));
      list.add(new CommandHelp.CommandInfo("/msgtoggle", "Toggle receiving PMs", "sharded.msg.toggle"));
      list.add(new CommandHelp.CommandInfo("/nightvision", "Toggle night vision", "sharded.nightvision.use"));
      list.add(new CommandHelp.CommandInfo("/backpack [player]", "Open backpack storage", "sharded.backpack.use"));
      list.add(new CommandHelp.CommandInfo("/armortrims (/trims)", "Armor trim station", "sharded.armortrims.use"));
      list.add(new CommandHelp.CommandInfo("/fly", "Toggle flight", "sharded.fly.use"));
      list.add(new CommandHelp.CommandInfo("/autosmelt", "Auto smelt pickaxe", "sharded.autosmelt.use"));
      list.add(new CommandHelp.CommandInfo("/rtp", "Random teleport menu", "sharded.rtp.use"));
      list.add(new CommandHelp.CommandInfo("/duel <player>", "Send a duel request (Duels plugin)", "duels.duel"));
      list.add(new CommandHelp.CommandInfo("/guide", "Server guide menu", "sharded.guide.use"));
      list.add(new CommandHelp.CommandInfo("/rules", "Server rules menu", "sharded.guide.use"));
      list.add(new CommandHelp.CommandInfo("/spawn (/spawnselect)", "Spawn selector", "sharded.spawn.use"));
      list.add(new CommandHelp.CommandInfo("/pets", "Open pets menu", "sharded.pets.view"));
      list.add(new CommandHelp.CommandInfo("/pet equip|remove|rename", "Manage cosmetic pet", "sharded.pets.use"));
      list.add(new CommandHelp.CommandInfo("/settings", "Personal settings menu", "sharded.settings.use"));
      list.add(new CommandHelp.CommandInfo("/kit (/kits, /k)", "Open kits menu or claim a kit", "sharded.kits"));
      list.add(new CommandHelp.CommandInfo("/bal (/balance)", "Check token balance", null));
      list.add(new CommandHelp.CommandInfo("/tokenshop", "Open token shop", "sharded.tokenshop.use"));
      list.add(new CommandHelp.CommandInfo("/temprank shop", "Temporary rank shop", "sharded.tempranks.use"));
      list.add(new CommandHelp.CommandInfo("/toolname [name]", "Rename held item", "sharded.toolname.use"));
      list.add(new CommandHelp.CommandInfo("/requeststaff", "Request staff help", "sharded.requeststaff.use"));
      list.add(new CommandHelp.CommandInfo("/tokens ...", "Token admin commands", "sharded.tokens.admin"));
      list.add(new CommandHelp.CommandInfo("/graves", "Graves admin", "sharded.graves.admin"));
      list.add(new CommandHelp.CommandInfo("/shardedcore reload", "Reload plugin", "sharded.admin"));
      list.add(new CommandHelp.CommandInfo("/shardedcore staff", "List all staff commands", "sharded.staff"));
      return list;
   }

   public static List<CommandHelp.CommandInfo> staff() {
      List<CommandHelp.CommandInfo> list = new ArrayList<>();
      list.add(new CommandHelp.CommandInfo("/staffmode (/sfmode)", "Toggle staff mode", "sharded.staff.mode"));
      list.add(new CommandHelp.CommandInfo("/vanish", "Toggle vanish", "sharded.staff.vanish"));
      list.add(new CommandHelp.CommandInfo("/freeze <player>", "Freeze a player", "sharded.staff.freeze"));
      list.add(new CommandHelp.CommandInfo("/stafflist", "List online staff", "sharded.staff.list"));
      list.add(new CommandHelp.CommandInfo("/randomtp", "Teleport to random player", "sharded.staff.randomtp"));
      list.add(new CommandHelp.CommandInfo("/staffchat (/sc)", "Toggle staff chat mode", "sharded.staffchat.use"));
      list.add(new CommandHelp.CommandInfo("/gmc /gms /gmsp", "Change gamemode", "sharded.staff.gamemode"));
      list.add(new CommandHelp.CommandInfo("/punish <player>", "Open punish menu", "sharded.staff.punish"));
      list.add(new CommandHelp.CommandInfo("/ban <player> [reason] [duration]", "Ban a player", "sharded.staff.ban"));
      list.add(new CommandHelp.CommandInfo("/mute <player> [reason] [duration]", "Mute a player", "sharded.staff.mute"));
      list.add(new CommandHelp.CommandInfo("/kick <player> [reason]", "Kick a player", "sharded.staff.kick"));
      list.add(new CommandHelp.CommandInfo("/offend <player>", "Ban repeat offender", "sharded.staff.offend"));
      list.add(new CommandHelp.CommandInfo("/banip <player> [reason]", "IP ban a player", "sharded.staff.banip"));
      list.add(new CommandHelp.CommandInfo("/unban <player>", "Unban a player", "sharded.staff.unban"));
      list.add(new CommandHelp.CommandInfo("/unbanip (/unban-ip) <ip|player|list>", "Remove an IP ban", "sharded.staff.unbanip"));
      list.add(new CommandHelp.CommandInfo("/unmute <player>", "Unmute a player", "sharded.staff.unmute"));
      list.add(new CommandHelp.CommandInfo("/pardon <player>", "Unban + unmute", "sharded.staff.pardon"));
      list.add(new CommandHelp.CommandInfo("/wipe <player>", "Wipe player data", "sharded.staff.wipe"));
      list.add(new CommandHelp.CommandInfo("/alts [player]", "Show linked alts", "sharded.staff.alts"));
      list.add(new CommandHelp.CommandInfo("/screenshare (/ss) <player>", "Screenshare a player", "sharded.staff.screenshare"));
      list.add(new CommandHelp.CommandInfo("/invrollback <player>", "Inventory rollback GUI", "sharded.staff.invrollback"));
      list.add(new CommandHelp.CommandInfo("/announce <message>", "Server announcement", "sharded.staff.announce"));
      list.add(new CommandHelp.CommandInfo("/revokepunishment", "Mass revoke punishments", "sharded.staff.revokepunishment"));
      return list;
   }

   public static void send(CommandSender sender, String headerPrefix) {
      send(sender, headerPrefix, null);
   }

   public static void send(CommandSender sender, String headerPrefix, ModuleManager modules) {
      sender.sendMessage(Text.c(headerPrefix + "&bShardedCore Commands:"));

      for (CommandHelp.CommandInfo commandhelp$commandinfo : all()) {
         if (commandhelp$commandinfo.permission() == null || sender.hasPermission(commandhelp$commandinfo.permission())) {
            boolean flag = commandhelp$commandinfo.moduleId() == null || modules == null || modules.isConfiguredEnabled(commandhelp$commandinfo.moduleId());
            String s = flag ? "&f" : "&c";
            sender.sendMessage(Text.c("&7- " + s + commandhelp$commandinfo.command() + " &8- &7" + commandhelp$commandinfo.description()));
         }
      }
   }

   public static void sendStaff(CommandSender sender, String headerPrefix) {
      if (!sender.hasPermission("sharded.staff")) {
         sender.sendMessage(Text.c(headerPrefix + "&cYou don't have permission."));
      } else {
         sender.sendMessage(Text.c(headerPrefix + "&bStaff Commands:"));

         for (CommandHelp.CommandInfo commandhelp$commandinfo : staff()) {
            if (commandhelp$commandinfo.permission() == null || sender.hasPermission(commandhelp$commandinfo.permission())) {
               sender.sendMessage(Text.c("&7- &f" + commandhelp$commandinfo.command() + " &8- &7" + commandhelp$commandinfo.description()));
            }
         }
      }
   }

   private static String moduleForCommand(String command) {
      String s = command.toLowerCase(Locale.ROOT).split("\\s")[0].replace("/", "");

      return switch (s) {
         case "craft" -> "craft";
         case "fix" -> "fix";
         case "trash" -> "trash";
         case "chattoggle", "togglechat", "ct", "publicchat" -> "chat";
         case "msg", "tell", "whisper", "w", "pm", "reply", "r", "msgtoggle", "togglemsg", "pmtoggle" -> "privatemessages";
         case "nightvision", "nv" -> "nightvision";
         case "backpack", "bp" -> "backpack";
         case "armortrims", "trims", "trimstation" -> "armortrims";
         case "fly" -> "fly";
         case "autosmelt" -> "autosmelt";
         case "rtp", "wild", "unlock" -> "portalrtp";
         case "duel" -> "duel";
         case "guide", "rules" -> "guide";
         case "spawn", "spawnselect", "spawnselector", "setspawn" -> "spawnselect";
         case "pets", "pet" -> "pets";
         case "settings", "setting" -> "settings";
         case "killstreak" -> "killstreaks";
         case "bal", "balance", "tokens", "tokenshop", "temprank", "rankshop", "temprankshop" -> "tokens";
         case "toolname" -> "toolname";
         case "requeststaff" -> "requeststaff";
         case "graves", "headtokens" -> "graves";
         default -> null;
      };
   }

   public static record CommandInfo(String command, String description, String permission, String moduleId) {
      public CommandInfo(String command, String description, String permission) {
         this(command, description, permission, CommandHelp.moduleForCommand(command));
      }
   }
}

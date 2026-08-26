package com.sharded.core.util;

import com.sharded.core.module.ModuleManager;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the /shardedcore command list for players. */
public final class CommandHelp {

    private CommandHelp() {
    }

    public record CommandInfo(String command, String description, String permission, String moduleId) {
        public CommandInfo(String command, String description, String permission) {
            this(command, description, permission, moduleForCommand(command));
        }
    }

    public static List<CommandInfo> all() {
        List<CommandInfo> list = new ArrayList<>();
        // Links & info
        list.add(new CommandInfo("/discord", "Discord server link", null, "links"));
        list.add(new CommandInfo("/store", "Webstore link", null, "links"));
        list.add(new CommandInfo("/apply", "Staff application link", null, "links"));
        list.add(new CommandInfo("/guide", "Server guide menu", "sharded.guide.use", "guide"));
        list.add(new CommandInfo("/rules", "Server rules menu", "sharded.guide.use", "guide"));
        list.add(new CommandInfo("/media", "Media rank applications", null, "media"));
        // Economy
        list.add(new CommandInfo("/bal (/balance /money)", "Check money balance", null, "economy"));
        list.add(new CommandInfo("/pay <player> <amount>", "Pay another player", "sharded.economy.pay", "economy"));
        list.add(new CommandInfo("/baltop (/moneytop)", "Money leaderboard", "sharded.economy.baltop", "economy"));
        list.add(new CommandInfo("/ecogive /ecoset /ecotake /ecoreset /ecofreeze", "Economy admin", "sharded.economy.admin", "economy"));
        // Teleport & homes
        list.add(new CommandInfo("/spawn", "Teleport to spawn", "sharded.spawn.use", "spawnselect"));
        list.add(new CommandInfo("/homes (/home /sethome /delhome)", "Home management GUI", null, "homes"));
        list.add(new CommandInfo("/tpa /tpahere /tpaccept /tpacancel", "Teleport requests", "sharded.tpa.use", "tpa"));
        list.add(new CommandInfo("/tpatoggle /tpauto", "TPA settings", "sharded.tpa.use", "tpa"));
        list.add(new CommandInfo("/rtp", "Random teleport menu", "sharded.rtp.use", "portalrtp"));
        // Live & social
        list.add(new CommandInfo("/live <url>", "Announce livestream", "sharded.live.use", "live"));
        list.add(new CommandInfo("/live toggle", "Toggle live alerts", "sharded.live.use", "live"));
        list.add(new CommandInfo("/ping [player]", "Check ping", "sharded.ping.use", "ping"));
        list.add(new CommandInfo("/chattoggle", "Toggle public chat", "sharded.chat.toggle", "chat"));
        list.add(new CommandInfo("/msg /reply /msgtoggle", "Private messages", "sharded.msg.use", "privatemessages"));
        list.add(new CommandInfo("/jointoggle", "Toggle join/leave messages", null, "joincounter"));
        list.add(new CommandInfo("/deathtoggle", "Toggle death messages", null, "deathmessages"));
        list.add(new CommandInfo("/mobtoggle", "Toggle mob spawning near you", null, "settings"));
        list.add(new CommandInfo("/paytoggle", "Block incoming payments", null, "settings"));
        list.add(new CommandInfo("/settings", "Personal settings menu", "sharded.settings.use", "settings"));
        // Workstations & utility
        list.add(new CommandInfo("/craft", "Portable crafting table", "sharded.craft.use", "craft"));
        list.add(new CommandInfo("/anvil /grindstone /smithingtable", "Portable workstations", null, "workstations"));
        list.add(new CommandInfo("/trash", "Open trash bin", "sharded.trash.use", "trash"));
        list.add(new CommandInfo("/fix", "Repair held item", "sharded.fix.use", "fix"));
        // Games & rewards
        list.add(new CommandInfo("/cf (/coinflip)", "Coinflip games", "sharded.coinflip.use", "coinflip"));
        list.add(new CommandInfo("/order (/orderadmin)", "Order board", null, "orders"));
        list.add(new CommandInfo("/sell /worth /sellmulti", "Sell items", null, "sell"));
        list.add(new CommandInfo("/shop", "Server shop", null, "shop"));
        list.add(new CommandInfo("/kits (/kit)", "Kit menu", null, "kits"));
        list.add(new CommandInfo("/killrewards", "Kill milestone rewards", null, "killrewards"));
        list.add(new CommandInfo("/playtimerewards", "Playtime rewards", null, "playtimerewards"));
        list.add(new CommandInfo("/team", "Team management", null, "teams"));
        list.add(new CommandInfo("/crate", "Custom crates", null, "crates"));
        // Admin
        list.add(new CommandInfo("/shardedcore reload", "Reload plugin", "sharded.admin"));
        list.add(new CommandInfo("/shardedcore features", "List all modules", "sharded.admin"));
        list.add(new CommandInfo("/shardedcore placeholders", "List placeholders", "sharded.admin"));
        return list;
    }

    public static List<CommandInfo> staff() {
        List<CommandInfo> list = new ArrayList<>();
        list.add(new CommandInfo("/staffmode (/sfmode)", "Toggle staff mode", "sharded.staff.mode"));
        list.add(new CommandInfo("/vanish", "Toggle vanish", "sharded.staff.vanish"));
        list.add(new CommandInfo("/freeze <player>", "Freeze a player", "sharded.staff.freeze"));
        list.add(new CommandInfo("/stafflist", "List online staff", "sharded.staff.list"));
        list.add(new CommandInfo("/randomtp", "Teleport to random player", "sharded.staff.randomtp"));
        list.add(new CommandInfo("/staffchat (/sc)", "Toggle staff chat mode", "sharded.staffchat.use"));
        list.add(new CommandInfo("/gmc /gms /gmsp", "Change gamemode", "sharded.staff.gamemode"));
        list.add(new CommandInfo("/punish <player>", "Open punish menu", "sharded.staff.punish"));
        list.add(new CommandInfo("/ban <player> [reason] [duration]", "Ban a player", "sharded.staff.ban"));
        list.add(new CommandInfo("/mute <player> [reason] [duration]", "Mute a player", "sharded.staff.mute"));
        list.add(new CommandInfo("/kick <player> [reason]", "Kick a player", "sharded.staff.kick"));
        list.add(new CommandInfo("/offend <player>", "Ban repeat offender", "sharded.staff.offend"));
        list.add(new CommandInfo("/banip <player> [reason]", "IP ban a player", "sharded.staff.banip"));
        list.add(new CommandInfo("/unban <player>", "Unban a player", "sharded.staff.unban"));
        list.add(new CommandInfo("/unbanip (/unban-ip) <ip|player|list>", "Remove an IP ban", "sharded.staff.unbanip"));
        list.add(new CommandInfo("/unmute <player>", "Unmute a player", "sharded.staff.unmute"));
        list.add(new CommandInfo("/pardon <player>", "Unban + unmute", "sharded.staff.pardon"));
        list.add(new CommandInfo("/wipe <player>", "Wipe player data", "sharded.staff.wipe"));
        list.add(new CommandInfo("/alts [player]", "Show linked alts", "sharded.staff.alts"));
        list.add(new CommandInfo("/screenshare (/ss) <player>", "Screenshare a player", "sharded.staff.screenshare"));
        list.add(new CommandInfo("/invrollback <player>", "Inventory rollback", "sharded.staff.invrollback"));
        list.add(new CommandInfo("/announce <message>", "Server announcement", "sharded.staff.announce"));
        list.add(new CommandInfo("/revokepunishment", "Mass revoke punishments", "sharded.staff.revokepunishment"));
        return list;
    }

    public static void send(CommandSender sender, String headerPrefix) {
        send(sender, headerPrefix, null);
    }

    public static void send(CommandSender sender, String headerPrefix, ModuleManager modules) {
        sender.sendMessage(Text.c(headerPrefix + "&bShardedCore Commands:"));
        for (CommandInfo info : all()) {
            if (info.permission() != null && !sender.hasPermission(info.permission())) continue;
            boolean enabled = info.moduleId() == null || modules == null || modules.isConfiguredEnabled(info.moduleId());
            String cmdColor = enabled ? "&f" : "&c";
            sender.sendMessage(Text.c("&7- " + cmdColor + info.command() + " &8- &7" + info.description()));
        }
    }

    public static void sendStaff(CommandSender sender, String headerPrefix) {
        if (!sender.hasPermission("sharded.staff")) {
            sender.sendMessage(Text.c(headerPrefix + "&cYou don't have permission."));
            return;
        }
        sender.sendMessage(Text.c(headerPrefix + "&bStaff Commands:"));
        for (CommandInfo info : staff()) {
            if (info.permission() != null && !sender.hasPermission(info.permission())) continue;
            sender.sendMessage(Text.c("&7- &f" + info.command() + " &8- &7" + info.description()));
        }
    }

    private static String moduleForCommand(String command) {
        String base = command.toLowerCase(Locale.ROOT).split("\\s")[0].replace("/", "");
        return switch (base) {
            case "discord", "store", "apply" -> "links";
            case "craft" -> "craft";
            case "anvil", "grindstone", "smithingtable" -> "workstations";
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
            case "home", "homes", "sethome", "delhome" -> "homes";
            case "tpa", "tpahere", "tpaccept", "tpacancel", "tpatoggle", "tpauto" -> "tpa";
            case "pets", "pet" -> "pets";
            case "settings", "setting", "paytoggle", "mobtoggle" -> "settings";
            case "killstreak" -> "killstreaks";
            case "live" -> "live";
            case "ping" -> "ping";
            case "bal", "balance", "money", "pay", "baltop", "moneytop", "ecogive", "ecoset", "ecotake", "ecoreset", "ecofreeze" -> "economy";
            case "tokens", "tokenshop", "temprank", "rankshop", "temprankshop" -> "tokens";
            case "toolname" -> "toolname";
            case "requeststaff" -> "requeststaff";
            case "graves", "headtokens" -> "graves";
            case "cf", "coinflip" -> "coinflip";
            case "order", "orderadmin" -> "orders";
            case "sell", "worth", "sellmulti" -> "sell";
            case "shop", "shops" -> "shop";
            case "kit", "kits" -> "kits";
            case "killrewards" -> "killrewards";
            case "playtimerewards" -> "playtimerewards";
            case "team", "teams" -> "teams";
            case "crate", "crates" -> "crates";
            case "media" -> "media";
            case "jointoggle", "joincounter" -> "joincounter";
            case "deathtoggle" -> "deathmessages";
            default -> null;
        };
    }
}

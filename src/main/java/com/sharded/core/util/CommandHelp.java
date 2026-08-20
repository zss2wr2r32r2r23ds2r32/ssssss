package com.sharded.core.util;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** Builds the /shardedcore command list for players. */
public final class CommandHelp {

    private CommandHelp() {
    }

    public record CommandInfo(String command, String description, String permission) {
    }

    public static List<CommandInfo> all() {
        List<CommandInfo> list = new ArrayList<>();
        list.add(new CommandInfo("/craft", "Portable crafting table", "sharded.craft.use"));
        list.add(new CommandInfo("/fix", "Repair held item", "sharded.fix.use"));
        list.add(new CommandInfo("/trash", "Open trash bin", "sharded.trash.use"));
        list.add(new CommandInfo("/chattoggle", "Toggle public chat", "sharded.chat.toggle"));
        list.add(new CommandInfo("/msg <player> <msg>", "Private message", "sharded.msg.use"));
        list.add(new CommandInfo("/reply <msg>", "Reply to last PM", "sharded.msg.use"));
        list.add(new CommandInfo("/msgtoggle", "Toggle receiving PMs", "sharded.msg.toggle"));
        list.add(new CommandInfo("/nightvision", "Toggle night vision", "sharded.nightvision.use"));
        list.add(new CommandInfo("/kill [player]", "Kill yourself or another player", "sharded.kill.use"));
        list.add(new CommandInfo("/backpack [player]", "Open backpack storage", "sharded.backpack.use"));
        list.add(new CommandInfo("/armortrims (/trims)", "Armor trim station", "sharded.armortrims.use"));
        list.add(new CommandInfo("/fly", "Toggle flight", "sharded.fly.use"));
        list.add(new CommandInfo("/autosmelt", "Auto smelt pickaxe", "sharded.autosmelt.use"));
        list.add(new CommandInfo("/rtp", "Random teleport menu", "sharded.rtp.use"));
        list.add(new CommandInfo("/guide", "Server guide menu", "sharded.guide.use"));
        list.add(new CommandInfo("/rules", "Server rules menu", "sharded.guide.use"));
        list.add(new CommandInfo("/spawn (/spawnselect)", "Spawn selector", "sharded.spawn.use"));
        list.add(new CommandInfo("/pets", "Open pets menu", "sharded.pets.use"));
        list.add(new CommandInfo("/pet equip|remove|rename", "Manage cosmetic pet", "sharded.pets.use"));
        list.add(new CommandInfo("/settings", "Personal settings menu", "sharded.settings.use"));
        list.add(new CommandInfo("/killstreak [best|player]", "View killstreak stats", "sharded.killstreak.use"));
        list.add(new CommandInfo("/bal (/balance)", "Check token balance", null));
        list.add(new CommandInfo("/tokenshop", "Open token shop", "sharded.tokenshop.use"));
        list.add(new CommandInfo("/temprank shop", "Temporary rank shop", "sharded.tempranks.use"));
        list.add(new CommandInfo("/toolname [name]", "Rename held item", "sharded.toolname.use"));
        list.add(new CommandInfo("/requeststaff", "Request staff help", "sharded.requeststaff.use"));
        list.add(new CommandInfo("/tokens ...", "Token admin commands", "sharded.tokens.admin"));
        list.add(new CommandInfo("/graves", "Graves admin", "sharded.graves.admin"));
        list.add(new CommandInfo("/shardedcore reload", "Reload plugin", "sharded.admin"));
        list.add(new CommandInfo("/shardedcore staff", "List all staff commands", "sharded.staff"));
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
        list.add(new CommandInfo("/unbanip (/unban-ip) <ip>", "Remove an IP ban", "sharded.staff.unbanip"));
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
        sender.sendMessage(Text.c(headerPrefix + "&bShardedCore Commands:"));
        for (CommandInfo info : all()) {
            if (info.permission() != null && !sender.hasPermission(info.permission())) continue;
            sender.sendMessage(Text.c("&7- &f" + info.command() + " &8- &7" + info.description()));
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
}

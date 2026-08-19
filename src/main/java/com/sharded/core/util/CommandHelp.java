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
        list.add(new CommandInfo("/pets", "Open pets menu", "sharded.pets.use"));
        list.add(new CommandInfo("/pet equip|remove|rename", "Manage cosmetic pet", "sharded.pets.use"));
        list.add(new CommandInfo("/sb", "Toggle scoreboard", "sharded.settings.scoreboard"));
        list.add(new CommandInfo("/deathtoggle", "Toggle death messages", "sharded.settings.deathmessages"));
        list.add(new CommandInfo("/jointoggle", "Toggle join/leave messages", "sharded.settings.joinmessages"));
        list.add(new CommandInfo("/mobtoggle", "Toggle mob spawning near you", "sharded.settings.mobspawn"));
        list.add(new CommandInfo("/killstreak [best|player]", "View killstreak stats", "sharded.killstreak.use"));
        list.add(new CommandInfo("/bal", "Check token balance", null));
        list.add(new CommandInfo("/tokenshop", "Open token shop", "sharded.tokenshop.use"));
        list.add(new CommandInfo("/toolname [name]", "Rename held item", "sharded.toolname.use"));
        list.add(new CommandInfo("/spawners pay", "Pay to pick up spawners", "sharded.spawners.use"));
        list.add(new CommandInfo("/tokens ...", "Token admin commands", "sharded.tokens.admin"));
        list.add(new CommandInfo("/graves", "Graves admin", "sharded.graves.admin"));
        list.add(new CommandInfo("/shardedcore reload", "Reload plugin", "sharded.admin"));
        return list;
    }

    public static void send(CommandSender sender, String headerPrefix) {
        sender.sendMessage(Text.c(headerPrefix + "&bShardedCore Commands:"));
        for (CommandInfo info : all()) {
            if (info.permission() != null && !sender.hasPermission(info.permission())) continue;
            sender.sendMessage(Text.c("&7- &f" + info.command() + " &8- &7" + info.description()));
        }
    }
}

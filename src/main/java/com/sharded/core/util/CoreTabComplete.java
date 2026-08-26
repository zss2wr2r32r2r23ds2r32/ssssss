package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.punishments.PunishmentDatabase;
import com.sharded.core.modules.punishments.PunishmentsModule;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Default tab completions for commands that do not define their own completer. */
public final class CoreTabComplete implements TabCompleter {

    private final ShardedCore plugin;

    public CoreTabComplete(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command == null) return List.of();
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return switch (name) {
                case "backpack", "punish", "ban", "banip", "kick", "mute", "offend",
                     "wipe", "invrollback", "screenshare", "freeze" -> TabCompleteHelper.onlinePlayers(args[0]);
                case "fly" -> {
                    List<String> options = new ArrayList<>(TabCompleteHelper.onlinePlayers(args[0]));
                    options.addAll(TabCompleteHelper.filter(args[0], "speed", "pos1", "pos2", "setregion"));
                    yield options;
                }
                case "alts" -> TabCompleteHelper.knownPlayers(args[0]);
                case "spawn", "setspawn" -> TabCompleteHelper.filter(args[0], "main", "vanilla");
                case "temprank", "rankshop", "temprankshop" -> TabCompleteHelper.filter(args[0], "shop");
                case "tokens" -> TabCompleteHelper.filter(args[0], "give", "set", "remove", "take", "reset", "giveall");
                case "pet" -> TabCompleteHelper.filter(args[0], "equip", "remove", "rename");
                case "eglow", "glows", "glowing" -> TabCompleteHelper.filter(args[0], "off");
                case "namecolor", "namecolors" -> TabCompleteHelper.filter(args[0], "custom");
                case "tag", "tags" -> TabCompleteHelper.filter(args[0], "custom");
                case "chattoggle", "msgtoggle", "deathtoggle", "jointoggle", "mobtoggle" ->
                        TabCompleteHelper.filter(args[0], "toggle");
                case "live" -> TabCompleteHelper.filter(args[0], "toggle");
                case "killstreak" -> TabCompleteHelper.filter(args[0], "best", "player");
                case "graves" -> TabCompleteHelper.filter(args[0], "reload", "clear", "list");
                default -> List.of();
            };
        }
        if (args.length == 2) {
            return switch (name) {
                case "msg", "tell", "whisper", "w", "pm" -> TabCompleteHelper.onlinePlayers(args[1]);
                case "backpack", "ping" -> TabCompleteHelper.onlinePlayers(args[1]);
                case "fly" -> {
                    if (args[0].equalsIgnoreCase("speed")) {
                        yield TabCompleteHelper.filter(args[1], "1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
                    }
                    yield TabCompleteHelper.onlinePlayers(args[1]);
                }
                case "tokens" -> switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "give", "set", "remove", "take", "reset" -> TabCompleteHelper.onlinePlayers(args[1]);
                    case "giveall" -> TabCompleteHelper.filter(args[1], "100", "500", "1000", "5000");
                    default -> List.of();
                };
                case "setspawn" -> worldNames(args[1]);
                case "pet" -> switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "equip", "rename" -> TabCompleteHelper.filter(args[1], "dog", "cat", "parrot", "axolotl", "allay");
                    default -> List.of();
                };
                case "killstreak" -> args[0].equalsIgnoreCase("player")
                        ? TabCompleteHelper.knownPlayers(args[1]) : List.of();
                default -> punishmentsSecondArg(name, args);
            };
        }
        if (args.length == 3) {
            PunishmentsModule punishments = plugin.modules().get(PunishmentsModule.class);
            if (punishments != null) {
                return switch (name) {
                    case "ban", "banip", "offend" -> TabCompleteHelper.configKeys(args[2], punishments.banReasons());
                    case "mute" -> TabCompleteHelper.configKeys(args[2], punishments.muteReasons());
                    case "kick" -> TabCompleteHelper.configKeys(args[2], punishments.kickReasons());
                    case "wipe" -> TabCompleteHelper.configKeys(args[2], punishments.wipeReasons());
                    default -> List.of();
                };
            }
        }
        return List.of();
    }

    private List<String> punishmentsSecondArg(String name, String[] args) {
        PunishmentsModule punishments = plugin.modules().get(PunishmentsModule.class);
        if (punishments == null) return List.of();
        return switch (name) {
            case "ban", "banip", "offend" -> TabCompleteHelper.configKeys(args[1], punishments.banReasons());
            case "mute" -> TabCompleteHelper.configKeys(args[1], punishments.muteReasons());
            case "kick" -> TabCompleteHelper.configKeys(args[1], punishments.kickReasons());
            case "wipe" -> TabCompleteHelper.configKeys(args[1], punishments.wipeReasons());
            case "unban" -> TabCompleteHelper.filter(args[1], punishments.database().activePunishedPlayerNames(PunishmentDatabase.PunishmentType.BAN));
            case "unbanip" -> TabCompleteHelper.filter(args[1], punishments.database().activeIpBans());
            case "unmute" -> TabCompleteHelper.filter(args[1], punishments.database().activePunishedPlayerNames(PunishmentDatabase.PunishmentType.MUTE));
            default -> List.of();
        };
    }

    private List<String> worldNames(String input) {
        List<String> worlds = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) worlds.add(world.getName());
        return TabCompleteHelper.filter(input, worlds);
    }
}

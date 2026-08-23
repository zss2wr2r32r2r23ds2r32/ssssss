package dev.sharded.velocitycore.lobby.maintenance;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MaintenanceCommand implements CommandExecutor, TabCompleter {

    private final MaintenanceManager maintenanceManager;

    public MaintenanceCommand(MaintenanceManager maintenanceManager) {
        this.maintenanceManager = maintenanceManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedvelocitycore.maintenance")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            boolean enabled = maintenanceManager.toggle();
            if (enabled) {
                sender.sendMessage("§cMaintenance enabled. Non-bypass players were kicked.");
            } else {
                sender.sendMessage("§aMaintenance disabled.");
            }
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "wipe" -> handleWipe(sender);
            default -> {
                sender.sendMessage("§eUsage: /maintenance [add|remove|wipe] [player]");
                yield true;
            }
        };
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /maintenance add <player>");
            return true;
        }

        String playerName = args[1];
        if (maintenanceManager.addBypass(playerName)) {
            sender.sendMessage("§aAdded §f" + playerName + " §ato the maintenance bypass list.");
        } else {
            sender.sendMessage("§cThat player is already on the bypass list.");
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: /maintenance remove <player>");
            return true;
        }

        String playerName = args[1];
        if (maintenanceManager.removeBypass(playerName)) {
            sender.sendMessage("§aRemoved §f" + playerName + " §afrom the maintenance bypass list.");
        } else {
            sender.sendMessage("§cThat player is not on the bypass list.");
        }
        return true;
    }

    private boolean handleWipe(CommandSender sender) {
        maintenanceManager.wipeBypass();
        sender.sendMessage("§aCleared the maintenance bypass list.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("shardedvelocitycore.maintenance")) {
            return List.of();
        }

        if (args.length == 1) {
            return filter(List.of("add", "remove", "wipe"), args[0]);
        }

        if (args.length == 2) {
            String subcommand = args[0].toLowerCase(Locale.ROOT);
            if (subcommand.equals("add")) {
                return filter(maintenanceManager.onlinePlayerSuggestions(), args[1]);
            }
            if (subcommand.equals("remove")) {
                return filter(maintenanceManager.bypassNames(), args[1]);
            }
        }

        return List.of();
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(option);
            }
        }
        return matches;
    }
}

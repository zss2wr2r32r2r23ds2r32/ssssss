package dev.sharded.core.chat;

import dev.sharded.core.ShardedCore;
import dev.sharded.core.chat.filter.FilterVerdict;
import dev.sharded.core.util.ColorUtil;
import dev.sharded.core.util.Similarity;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChatCommands implements CommandExecutor, TabCompleter {
    private final ShardedCore plugin;

    public ChatCommands(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedcore.chatfilter.admin")) {
            sender.sendMessage(plugin.messages().get("core.no-permission"));
            return true;
        }
        if (command.getName().equalsIgnoreCase("chathistory")) {
            return history(sender, args);
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            click(sender);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "regex" -> regex(sender, args);
            case "similar" -> similar(sender, args);
            case "test" -> test(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                sender.sendMessage(plugin.messages().get("core.reloaded"));
                click(sender);
                yield true;
            }
            default -> {
                help(sender);
                yield true;
            }
        };
    }

    private boolean regex(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.chatFilter().component("usage-regex"));
            return true;
        }
        String word = args[1];
        if (Similarity.uniqueLetterCount(word) < 3) {
            sender.sendMessage(plugin.chatFilter().component("regex-invalid"));
            return true;
        }
        StringBuilder rule = new StringBuilder();
        boolean added = plugin.chatFilter().addRegex(word, rule);
        if (!added) {
            sender.sendMessage(plugin.chatFilter().component("regex-known", "%word%", word, "%rule%", rule.toString()));
            return true;
        }
        sender.sendMessage(plugin.chatFilter().component("regex-added", "%word%", word, "%rule%", rule.toString()));
        click(sender);
        return true;
    }

    private boolean similar(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.chatFilter().component("usage-similar"));
            return true;
        }
        int percent = Similarity.percent(args[1], args[2]);
        sender.sendMessage(plugin.chatFilter().component(
                "similar",
                "%first%", args[1],
                "%second%", args[2],
                "%percent%", String.valueOf(percent)
        ));
        click(sender);
        return true;
    }

    private boolean test(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.chatFilter().component("usage-test"));
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        FilterVerdict verdict = plugin.chatFilter().engine().test(message);
        if (verdict.status() == FilterVerdict.Status.ALLOW) {
            sender.sendMessage(plugin.chatFilter().component("test-clean"));
        } else {
            sender.sendMessage(plugin.chatFilter().component(
                    "test-hit",
                    "%rule%", verdict.rule(),
                    "%action%", verdict.action(),
                    "%result%", verdict.blocked() ? verdict.original() : verdict.result()
            ));
        }
        click(sender);
        return true;
    }

    private boolean history(CommandSender sender, String[] args) {
        String player = args.length >= 1 ? args[0] : null;
        List<Map<String, Object>> entries = plugin.chatFilter().history(player);
        if (entries.isEmpty()) {
            sender.sendMessage(plugin.chatFilter().component("history-empty"));
            return true;
        }
        int from = Math.max(0, entries.size() - 20);
        for (int i = from; i < entries.size(); i++) {
            Map<String, Object> entry = entries.get(i);
            sender.sendMessage(ColorUtil.parse(
                    "&#FF0000&lFILTER &7▷ &f" + entry.get("player")
                            + " &7» &f" + entry.get("message")
                            + " &8(" + entry.get("rule") + " " + entry.get("action") + ")"
            ));
        }
        click(sender);
        return true;
    }

    private void help(CommandSender sender) {
        for (String line : plugin.chatFilter().helpLines()) {
            sender.sendMessage(ColorUtil.parse(line));
        }
    }

    private void click(CommandSender sender) {
        if (sender instanceof Player player) {
            plugin.chatFilter().playClick(player);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedcore.chatfilter.admin")) {
            return List.of();
        }
        if (command.getName().equalsIgnoreCase("chathistory")) {
            return args.length == 1 ? plugin.players().tabNames(args[0]) : List.of();
        }
        if (args.length == 1) {
            return List.of("regex", "similar", "test", "help", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}

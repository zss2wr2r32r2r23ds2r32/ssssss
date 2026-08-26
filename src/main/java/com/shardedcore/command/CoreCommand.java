package com.shardedcore.command;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Tabs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CoreCommand implements CommandExecutor, TabCompleter {

    private final ShardedCore plugin;

    public CoreCommand(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shardedcore.admin")) {
            sender.sendMessage(ColorUtil.parse("&#FF0000&lERROR &7▷ &fYou do not have permission."));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadPlugin();
                sender.sendMessage(ColorUtil.parse("&#97F900&lCORE &7▷ &fShardedCore reloaded."));
            }
            case "modules", "features" -> {
                if (args.length >= 3) {
                    String id = args[1].toLowerCase(Locale.ROOT);
                    boolean on = args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("enable")
                            || args[2].equalsIgnoreCase("true");
                    if (plugin.modules().setEnabled(id, on)) {
                        sender.sendMessage(ColorUtil.parse("&#97F900&lCORE &7▷ &fModule &#97F900" + id
                                + " &fis now " + (on ? "&#97F900enabled" : "&#FF0000disabled") + "&f."));
                    } else {
                        sender.sendMessage(ColorUtil.parse("&#FF0000&lERROR &7▷ &fUnknown module &#FF0000" + id + "&f."));
                    }
                    return true;
                }
                if (sender instanceof Player player && args.length == 1) {
                    plugin.modules().openGui(player, 0);
                    return true;
                }
                list(sender);
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(ColorUtil.parse("&#A370EE&lSHARDEDCORE"));
        sender.sendMessage(ColorUtil.parse("&7/shardedcore reload"));
        sender.sendMessage(ColorUtil.parse("&7/shardedcore modules"));
        sender.sendMessage(ColorUtil.parse("&7/shardedcore modules <name> <on|off>"));
        sender.sendMessage(ColorUtil.parse("&7/modules"));
    }

    private void list(CommandSender sender) {
        sender.sendMessage(ColorUtil.parse("&#A370EE&lMODULES"));
        for (Module module : plugin.modules().registered()) {
            boolean on = plugin.modules().isEnabled(module.id());
            sender.sendMessage(ColorUtil.parse((on ? "&#97F900" : "&#FF0000") + "● &f" + module.id()
                    + " &8- " + (on ? "&#97F900ENABLED" : "&#FF0000DISABLED")));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("shardedcore.admin")) return List.of();
        if (args.length == 1) return Tabs.filter(List.of("reload", "modules", "help"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("modules")) {
            List<String> ids = new ArrayList<>();
            plugin.modules().registered().forEach(module -> ids.add(module.id()));
            return Tabs.filter(ids, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("modules")) {
            return Tabs.filter(List.of("on", "off"), args[2]);
        }
        return List.of();
    }
}

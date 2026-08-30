package com.shardedcore.command;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Tabs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Bound to every command whose module is off so the client paints the label red. */
public final class DisabledCommand implements CommandExecutor, TabCompleter {

    private final ShardedCore plugin;

    public DisabledCommand(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String message = plugin.getConfig().getString("disabled-command",
                "&#FF0000&lERROR &8▷ &fThat module is currently &#FF0000disabled&f.");
        sender.sendMessage(ColorUtil.parse(message.replace("%command%", label)));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        return Tabs.filter(List.of(), args.length == 0 ? "" : args[args.length - 1]);
    }
}

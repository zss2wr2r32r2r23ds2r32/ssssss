package com.shardedcore.command;

import com.shardedcore.ShardedCore;
import com.shardedcore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class StubCommand implements CommandExecutor {

    private final ShardedCore plugin;

    public StubCommand(ShardedCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil.send(sender, plugin,
                "<yellow>Command <white>/" + label + "</white> is registered but not implemented yet.</yellow>");
        return true;
    }
}

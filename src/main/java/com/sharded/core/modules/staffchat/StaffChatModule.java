package com.sharded.core.modules.staffchat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Staff-only chat channel. */
public final class StaffChatModule extends Module implements CommandExecutor {

    public StaffChatModule(ShardedCore plugin) {
        super(plugin, "staffchat");
    }

    @Override
    protected void onEnable() {
        registerCommand("staffchat", this);
        registerCommand("sc", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String perm = config.getString("permission", "sharded.staffchat.use");
        if (!sender.hasPermission(perm)) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        String name = sender.getName() == null ? "Console" : sender.getName();
        String message = String.join(" ", args);
        String formatted = raw("format", "%player%", name, "%message%", message);
        var component = Text.c(formatted);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(perm)) online.sendMessage(component);
        }
        if (!(sender instanceof Player)) Bukkit.getConsoleSender().sendMessage(component);
        return true;
    }
}

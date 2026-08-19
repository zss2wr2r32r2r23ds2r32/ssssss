package com.sharded.core.modules.announce;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

/** Server-wide announcements. */
public final class AnnounceModule extends Module implements CommandExecutor, TabCompleter {

    public AnnounceModule(ShardedCore plugin) {
        super(plugin, "announce");
    }

    @Override
    protected void onEnable() {
        registerCommand("announce", this);
        registerCommand("announcement", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.announce.use")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        String message = String.join(" ", args);
        String formatted = raw("format", "%message%", message, "%sender%", sender.getName() == null ? "Console" : sender.getName());
        Bukkit.broadcast(Text.c(formatted));
        send(sender, "sent");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}

package com.shardedcore.modules.commands.announce;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.MessageUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public final class AnnounceModule extends Module implements CommandExecutor, TabCompleter {

    public AnnounceModule(ShardedCore plugin) {
        super(plugin, "announce");
    }

    @Override
    public void enable() {
        registerCommand("announce", this);
        registerCommand("announcement", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedcore.command.announce")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        broadcast(String.join(" ", args));
        send(sender, "sent");
        return true;
    }

    private void broadcast(String message) {
        String mode = config.getString("display-mode", "title").toLowerCase(Locale.ROOT);
        if (mode.equals("title") || mode.equals("both")) {
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(config.getInt("title-fade-in", 10) * 50L),
                    Duration.ofMillis(config.getInt("title-stay", 70) * 50L),
                    Duration.ofMillis(config.getInt("title-fade-out", 20) * 50L));
            Title title = Title.title(
                    ColorUtil.parse(config.getString("title-text", "&#00A2FF&lANNOUNCEMENT")),
                    ColorUtil.parse(message),
                    times);
            for (Player player : Bukkit.getOnlinePlayers()) player.showTitle(title);
        }
        if (mode.equals("chat") || mode.equals("both")) {
            String formatted = raw("format", "message", message);
            var component = ColorUtil.parse(formatted);
            for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(component);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}

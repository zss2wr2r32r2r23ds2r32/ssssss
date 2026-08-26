package com.shardedcore.modules.announce;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ColorUtil;
import com.shardedcore.util.Sounds;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;

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
        if (!sender.hasPermission("shardedcore.announce")) {
            send(sender, "messages.no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "messages.usage");
            return true;
        }
        String color = cfg("color", "&#A370EE");
        String titleText = cfg("title", color + "&lANNOUNCEMENT");
        String subtitle = color + String.join(" ", args);
        Title title = Title.title(
                ColorUtil.parse(titleText),
                ColorUtil.parse(subtitle),
                Title.Times.times(
                        Duration.ofMillis(config.getInt("fade-in", 10) * 50L),
                        Duration.ofMillis(config.getInt("stay", 70) * 50L),
                        Duration.ofMillis(config.getInt("fade-out", 20) * 50L)
                )
        );
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            Sounds.play(player, config.getConfigurationSection("sound"));
        }
        send(sender, "messages.sent");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}

package com.sharded.core.modules.announce;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.Text;
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

/** Server-wide announcements (title, chat, or both). */
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
        if (!sender.hasPermission("sharded.announce.use") && !sender.hasPermission("sharded.staff.announce")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        String message = String.join(" ", args);
        broadcastAnnouncement(message);
        send(sender, "sent");
        return true;
    }

    private void broadcastAnnouncement(String message) {
        String mode = config.getString("display-mode", "title").toLowerCase(Locale.ROOT);
        boolean useTitle = mode.equals("title") || mode.equals("both");
        boolean useChat = mode.equals("chat") || mode.equals("both");

        if (useTitle) {
            String titleText = config.getString("title-text", "&#00A2FF&lANNOUNCEMENT");
            int fadeIn = config.getInt("title-fade-in", 10);
            int stay = config.getInt("title-stay", 70);
            int fadeOut = config.getInt("title-fade-out", 20);
            Title.Times times = Title.Times.times(
                    Duration.ofMillis(fadeIn * 50L),
                    Duration.ofMillis(stay * 50L),
                    Duration.ofMillis(fadeOut * 50L));
            var title = Title.title(Text.c(titleText), Text.c(message), times);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(title);
            }
        }

        if (useChat) {
            String formatted = raw("format", "%message%", message);
            MessageUtil.Delivery delivery = resolveDelivery("broadcast");
            var component = Text.c(formatted);
            for (Player player : Bukkit.getOnlinePlayers()) {
                MessageUtil.deliver(player, component, delivery);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}

package com.sharded.core.modules.joincounter;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.PlayerToggles;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Unique join counter, first-join broadcasts, join/quit messages, and /jointoggle. */
public final class JoinCounterModule extends Module implements CommandExecutor, TabCompleter {

    private JoinCounterDatabase database;

    public JoinCounterModule(ShardedCore plugin) {
        super(plugin, "joincounter");
    }

    @Override
    protected void onEnable() {
        try {
            database = new JoinCounterDatabase(plugin, moduleFolder());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open join counter database", e);
        }
        registerCommand("joincounter", this);
        registerCommand("jointoggle", this);
    }

    @Override
    protected void onDisable() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    public long counter() {
        return database == null ? 0L : database.counter();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("jointoggle") || cmd.equals("joinmessages") || cmd.equals("joinleave") || cmd.equals("jtoggle")) {
            return handleJoinToggle(sender, args);
        }
        if (args.length == 0) {
            send(sender, "counter", "%count%", String.valueOf(counter()));
            return true;
        }
        if (!sender.hasPermission("sharded.joincounter.admin")) {
            send(sender, "no-permission");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reset", "clear" -> {
                database.resetCounter();
                send(sender, "counter-reset", "%count%", String.valueOf(counter()));
            }
            case "set" -> {
                if (args.length < 2) {
                    send(sender, "usage-set");
                    return true;
                }
                try {
                    long value = Long.parseLong(args[1]);
                    database.setCounter(Math.max(0L, value));
                    send(sender, "counter-set", "%count%", String.valueOf(counter()));
                } catch (NumberFormatException e) {
                    send(sender, "invalid-number");
                }
            }
            default -> send(sender, "usage");
        }
        return true;
    }

    private boolean handleJoinToggle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("toggle")) {
            return true;
        }
        if (!player.hasPermission("sharded.settings.joinmessages")) {
            send(player, "no-permission");
            return true;
        }
        PlayerToggles.setJoinMessages(player, !PlayerToggles.joinMessages(player));
        send(player, PlayerToggles.joinMessages(player) ? "toggle-on" : "toggle-off");
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(null);

        boolean firstJoin = database.markJoined(player.getUniqueId());
        if (firstJoin && config.getBoolean("counter.enabled", true)) {
            long count = database.counter();
            broadcast(config.getString("counter.first-join-broadcast", ""),
                    player, count, "first-join");
        }

        String type = firstJoin ? "first-join" : "join";
        String message = resolve(player, type);
        if (message == null && firstJoin) message = resolve(player, "join");
        if (message != null) {
            broadcast(message, player, counter(), type);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        String message = resolve(event.getPlayer(), "quit");
        if (message != null) {
            broadcast(message, event.getPlayer(), counter(), "quit");
        }
    }

    private void broadcast(String template, Player player, long count, String type) {
        if (template == null || template.isBlank()) return;
        String message = applyPlaceholders(template, player, count);
        var component = Text.c(message);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!PlayerToggles.joinMessages(viewer)) continue;
            viewer.sendMessage(component);
        }
    }

    private String resolve(Player player, String type) {
        ConfigurationSection formats = config.getConfigurationSection("formats");
        if (formats == null) return null;

        ConfigurationSection best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (String key : formats.getKeys(false)) {
            ConfigurationSection format = formats.getConfigurationSection(key);
            if (format == null) continue;
            String permission = format.getString("permission", "");
            if (!permission.isEmpty() && !player.hasPermission(permission)) continue;
            int priority = format.getInt("priority", 0);
            if (priority > bestPriority) {
                bestPriority = priority;
                best = format;
            }
        }
        if (best == null) return null;
        String message = best.getString(type, "");
        return message == null || message.isBlank() ? null : message;
    }

    private String applyPlaceholders(String message, Player player, long count) {
        return Text.apply(message,
                "%player%", player.getName(),
                "%count%", String.valueOf(count),
                "%counter%", String.valueOf(count),
                "%rank%", plugin.luckPerms().prefix(player),
                "%group%", plugin.luckPerms().primaryGroup(player));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("jointoggle")) {
            return args.length == 1 ? TabCompleteHelper.filter(args[0], "toggle") : List.of();
        }
        if (!sender.hasPermission("sharded.joincounter.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "reset", "clear", "set");
        return List.of();
    }
}

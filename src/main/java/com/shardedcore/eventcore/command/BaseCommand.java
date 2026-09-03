package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Shared plumbing for the commands: permission checks, module gating and
 * case-insensitive tab completion filtering.
 */
public abstract class BaseCommand implements CommandExecutor, TabCompleter {

    protected final ShardedEventCore plugin;
    private final String permission;
    private final Class<? extends EventModule> moduleType;

    protected BaseCommand(ShardedEventCore plugin, String permission, Class<? extends EventModule> moduleType) {
        this.plugin = plugin;
        this.permission = permission;
        this.moduleType = moduleType;
    }

    @Override
    public final boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            plugin.messages().send(sender, "no-permission", "%permission%", permission);
            return true;
        }
        if (moduleType != null) {
            EventModule module = plugin.modules().byType(moduleType);
            if (module == null || !module.isEnabled()) {
                plugin.messages().send(sender, "module-disabled",
                        "%module%", module == null ? "unknown" : module.id());
                return true;
            }
        }
        execute(sender, label, args);
        return true;
    }

    @Override
    public final List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (permission != null && !permission.isEmpty() && !sender.hasPermission(permission)) {
            return Collections.emptyList();
        }
        return complete(sender, args);
    }

    protected abstract void execute(CommandSender sender, String label, String[] args);

    protected List<String> complete(CommandSender sender, String[] args) {
        return Collections.emptyList();
    }

    /** Filters candidates by the token being typed. */
    protected static List<String> filter(Collection<String> candidates, String token) {
        String prefix = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>(Math.min(16, candidates.size()));
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(candidate);
            }
        }
        return out;
    }

    protected static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : org.bukkit.Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    protected Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        plugin.messages().send(sender, "players-only");
        return null;
    }

    protected static String join(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int index = from; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }
}

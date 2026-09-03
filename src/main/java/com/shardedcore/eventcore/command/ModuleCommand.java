package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /module list|enable|disable|toggle|info|reload}
 *
 * <p>Enabling and disabling takes effect immediately — a disabled module
 * unregisters its listeners and cancels its tasks — and the choice is written to
 * {@code modules/<id>.yml} so it survives a restart.</p>
 */
public final class ModuleCommand extends BaseCommand {

    private static final List<String> ACTIONS = List.of("list", "enable", "disable", "toggle", "info", "reload");

    public ModuleCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.module", null);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            list(sender);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "enable" -> setState(sender, args, label, true);
            case "disable" -> setState(sender, args, label, false);
            case "toggle" -> toggle(sender, args, label);
            case "info" -> info(sender, args, label);
            case "reload" -> {
                plugin.modules().reloadAll();
                plugin.guis().invalidateAll();
                plugin.messages().send(sender, "module.reloaded");
            }
            default -> plugin.messages().send(sender, "module.usage", "%label%", label);
        }
    }

    private void list(CommandSender sender) {
        plugin.messages().send(sender, "module.list-header",
                "%count%", Integer.toString(plugin.modules().all().size()));
        for (EventModule module : plugin.modules().all()) {
            plugin.messages().send(sender, "module.list-entry",
                    "%module%", module.id(),
                    "%status%", statusText(module.isEnabled()),
                    "%description%", module.description());
        }
    }

    private String statusText(boolean enabled) {
        return plugin.messages().raw(enabled ? "module.status-enabled" : "module.status-disabled");
    }

    private void setState(CommandSender sender, String[] args, String label, boolean value) {
        if (args.length < 2) {
            plugin.messages().send(sender, "module.usage", "%label%", label);
            return;
        }
        EventModule module = plugin.modules().byId(args[1]);
        if (module == null) {
            plugin.messages().send(sender, "module.unknown", "%module%", args[1]);
            return;
        }
        boolean changed = plugin.modules().setEnabled(module, value);
        plugin.messages().send(sender, changed ? "module.changed" : "module.unchanged",
                "%module%", module.id(),
                "%status%", statusText(module.isEnabled()));
        plugin.guis().invalidateAll();
    }

    private void toggle(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "module.usage", "%label%", label);
            return;
        }
        EventModule module = plugin.modules().byId(args[1]);
        if (module == null) {
            plugin.messages().send(sender, "module.unknown", "%module%", args[1]);
            return;
        }
        setState(sender, args, label, !module.isEnabled());
    }

    private void info(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "module.usage", "%label%", label);
            return;
        }
        EventModule module = plugin.modules().byId(args[1]);
        if (module == null) {
            plugin.messages().send(sender, "module.unknown", "%module%", args[1]);
            return;
        }
        plugin.messages().send(sender, "module.info",
                "%module%", module.id(),
                "%status%", statusText(module.isEnabled()),
                "%description%", module.description(),
                "%file%", "modules/" + module.id() + ".yml");
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(ACTIONS, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("reload")) {
            return filter(new ArrayList<>(plugin.modules().ids()), args[1]);
        }
        return Collections.emptyList();
    }
}

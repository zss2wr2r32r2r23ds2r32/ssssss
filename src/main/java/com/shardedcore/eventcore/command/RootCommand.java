package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.event.Setting;
import com.shardedcore.eventcore.modules.WorldBorderModule;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/** {@code /shardedeventcore reload|status} */
public final class RootCommand extends BaseCommand {

    private static final List<String> ACTIONS = List.of("reload", "status");

    public RootCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.admin", null);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "root.usage", "%label%", label,
                    "%version%", plugin.getPluginMeta().getVersion());
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadEverything();
                plugin.messages().send(sender, "root.reloaded");
            }
            case "status" -> status(sender);
            default -> plugin.messages().send(sender, "root.usage", "%label%", label,
                    "%version%", plugin.getPluginMeta().getVersion());
        }
    }

    private void status(CommandSender sender) {
        WorldBorderModule border = plugin.modules().byType(WorldBorderModule.class);
        EventMode mode = plugin.state().selected();

        plugin.messages().send(sender, "root.status",
                "%version%", plugin.getPluginMeta().getVersion(),
                "%mode%", mode == null ? "none" : mode.id(),
                "%phase%", plugin.state().phase().name().toLowerCase(Locale.ROOT),
                "%alive%", Integer.toString(plugin.state().aliveCount()),
                "%dead%", Integer.toString(plugin.state().dead().size()),
                "%border%", border == null || !border.isEnabled() ? "n/a" : border.formattedSize(),
                "%pvp%", Boolean.toString(plugin.state().toggleValue(Setting.PVP)),
                "%modules%", Long.toString(plugin.modules().all().stream()
                        .filter(module -> module.isEnabled()).count()),
                "%modules-total%", Integer.toString(plugin.modules().all().size()));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        return args.length == 1 ? filter(ACTIONS, args[0]) : List.of();
    }
}

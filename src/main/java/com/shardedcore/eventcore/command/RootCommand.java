package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.event.Setting;
import com.shardedcore.eventcore.modules.BedrockDropModule;
import com.shardedcore.eventcore.modules.ClearBlocksModule;
import com.shardedcore.eventcore.modules.GameModule;
import com.shardedcore.eventcore.modules.ProtectionModule;
import com.shardedcore.eventcore.modules.SpawnModule;
import com.shardedcore.eventcore.modules.SupplyDropModule;
import com.shardedcore.eventcore.modules.WorldBorderModule;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * {@code /shardedeventcore <reload|status|select|border|bedrockdrop|clearblocks|supplydrops|revive>}
 *
 * <p>The action subcommands do the same work as the icons in {@code /settings}.
 * They exist so the destructive operations can also be driven from the console,
 * a command block or another plugin, which the menu alone cannot do.</p>
 */
public final class RootCommand extends BaseCommand {

    private static final List<String> ACTIONS = List.of("reload", "status", "select", "border",
            "bedrockdrop", "clearblocks", "supplydrops", "revive");

    public RootCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.admin", null);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadEverything();
                plugin.messages().send(sender, "root.reloaded");
            }
            case "status" -> status(sender);
            case "select" -> select(sender, args, label);
            case "border" -> border(sender, args, label);
            case "bedrockdrop" -> bedrockDrop(sender);
            case "clearblocks" -> clearBlocks(sender);
            case "supplydrops" -> supplyDrops(sender, args);
            case "revive" -> revive(sender);
            default -> usage(sender, label);
        }
    }

    private void usage(CommandSender sender, String label) {
        plugin.messages().send(sender, "root.usage", "%label%", label,
                "%version%", plugin.getPluginMeta().getVersion());
    }

    private void select(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            usage(sender, label);
            return;
        }
        if (args[1].equalsIgnoreCase("none")) {
            plugin.state().select(null);
            plugin.messages().send(sender, "settings.unselected", "%mode%", "none");
            refreshAfterSelection();
            return;
        }
        EventMode mode = EventMode.fromId(args[1]);
        if (mode == null) {
            plugin.messages().send(sender, "spawn.unknown-mode", "%input%", args[1]);
            return;
        }
        plugin.state().select(mode);
        plugin.messages().send(sender, "settings.selected", "%mode%", mode.id());
        refreshAfterSelection();
    }

    private void refreshAfterSelection() {
        ProtectionModule protection = plugin.modules().byType(ProtectionModule.class);
        if (protection != null && protection.isEnabled()) {
            protection.applyWorldRules();
        }
        SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
        if (spawnModule != null && spawnModule.isEnabled()) {
            spawnModule.refreshCache();
        }
        plugin.guis().refreshAll();
    }

    private void border(CommandSender sender, String[] args, String label) {
        WorldBorderModule module = plugin.modules().byType(WorldBorderModule.class);
        if (module == null || !module.isEnabled()) {
            plugin.messages().send(sender, "settings.module-disabled", "%module%", "worldborder");
            return;
        }
        if (args.length < 2) {
            usage(sender, label);
            return;
        }
        String input = args.length > 2 ? args[1] + " " + args[2] : args[1];
        WorldBorderModule.Request request = module.parse(input);
        if (request == null) {
            plugin.messages().send(sender, "worldborder.invalid", "%input%", input);
            return;
        }
        double applied = module.apply(request.size(), request.millis());
        if (applied < 0.0D) {
            plugin.messages().send(sender, "worldborder.no-world");
            return;
        }
        plugin.messages().send(sender, "worldborder.applied",
                "%size%", Long.toString(Math.round(applied)),
                "%duration%", WorldBorderModule.formatMillis(request.millis()));
    }

    private void bedrockDrop(CommandSender sender) {
        BedrockDropModule module = plugin.modules().byType(BedrockDropModule.class);
        if (module == null || !module.isEnabled()) {
            plugin.messages().send(sender, "settings.module-disabled", "%module%", "bedrockdrop");
            return;
        }
        if (!module.start(cleared -> {
            plugin.messages().send(sender, "bedrockdrop.finished", "%blocks%", Long.toString(cleared));
            module.dropPlayers();
            plugin.guis().refreshAll();
        })) {
            plugin.messages().send(sender, "bedrockdrop.busy");
            return;
        }
        plugin.messages().send(sender, "bedrockdrop.started");
    }

    private void clearBlocks(CommandSender sender) {
        ClearBlocksModule module = plugin.modules().byType(ClearBlocksModule.class);
        if (module == null || !module.isEnabled()) {
            plugin.messages().send(sender, "settings.module-disabled", "%module%", "clearblocks");
            return;
        }
        if (!module.clear(counts -> {
            plugin.messages().send(sender, "clearblocks.finished",
                    "%blocks%", Integer.toString(counts[0]),
                    "%entities%", Integer.toString(counts[1]));
            plugin.guis().refreshAll();
        })) {
            plugin.messages().send(sender, "clearblocks.busy");
            return;
        }
        plugin.messages().send(sender, "clearblocks.started");
    }

    private void supplyDrops(CommandSender sender, String[] args) {
        SupplyDropModule module = plugin.modules().byType(SupplyDropModule.class);
        if (module == null || !module.isEnabled()) {
            plugin.messages().send(sender, "settings.module-disabled", "%module%", "supplydrops");
            return;
        }
        int count = module.defaultCount();
        if (args.length > 1) {
            try {
                count = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException exception) {
                plugin.messages().send(sender, "countdown.invalid", "%input%", args[1]);
                return;
            }
        }
        if (!module.spawn(count, placed -> plugin.messages().send(sender, "supplydrops.finished",
                "%count%", Integer.toString(placed)))) {
            plugin.messages().send(sender, "supplydrops.failed");
            return;
        }
        plugin.messages().send(sender, "supplydrops.started", "%count%", Integer.toString(count));
    }

    private void revive(CommandSender sender) {
        GameModule game = plugin.modules().byType(GameModule.class);
        if (game == null || !game.isEnabled()) {
            plugin.messages().send(sender, "settings.module-disabled", "%module%", "game");
            return;
        }
        plugin.messages().send(sender, "game.revived-all",
                "%players%", Integer.toString(game.reviveAll()));
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
        if (args.length == 1) {
            return filter(ACTIONS, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("select")) {
            List<String> modes = new java.util.ArrayList<>();
            for (EventMode mode : EventMode.values()) {
                modes.add(mode.id());
            }
            modes.add("none");
            return filter(modes, args[1]);
        }
        return List.of();
    }
}

package com.shardedcore.eventcore.command;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.modules.KitModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /kit create|give|delete|list}
 *
 * <p>{@code /kit create <name>} snapshots the sender's inventory, armour and
 * offhand. Handing that kit out later re-equips the armour and offhand
 * automatically, so a kit built anywhere in the inventory still lands correctly.</p>
 */
public final class KitCommand extends BaseCommand {

    private static final List<String> ACTIONS = List.of("create", "give", "delete", "list");

    public KitCommand(ShardedEventCore plugin) {
        super(plugin, "shardedcore.kit", KitModule.class);
    }

    @Override
    protected void execute(CommandSender sender, String label, String[] args) {
        KitModule kits = plugin.modules().byType(KitModule.class);

        if (args.length == 0) {
            plugin.messages().send(sender, "kits.usage", "%label%", label);
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create", "save" -> create(sender, kits, args, label);
            case "give" -> give(sender, kits, args, label);
            case "delete", "remove" -> delete(sender, kits, args, label);
            case "list" -> list(sender, kits);
            default -> plugin.messages().send(sender, "kits.usage", "%label%", label);
        }
    }

    private void create(CommandSender sender, KitModule kits, String[] args, String label) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "kits.create-usage", "%label%", label);
            return;
        }
        String name = args[1];
        kits.create(name, player);
        plugin.messages().send(sender, "kits.created", "%kit%", name.toLowerCase(Locale.ROOT));
        plugin.guis().refreshAll();
    }

    private void give(CommandSender sender, KitModule kits, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "kits.give-usage", "%label%", label);
            return;
        }
        String name = args[1];
        if (!kits.exists(name)) {
            plugin.messages().send(sender, "kits.missing", "%kit%", name);
            return;
        }

        if (args.length >= 3 && args[2].equalsIgnoreCase("all")) {
            int served = kits.giveEveryone(name);
            plugin.messages().send(sender, "kits.given-all",
                    "%kit%", name, "%players%", Integer.toString(Math.max(0, served)));
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found", "%player%", args[2]);
                return;
            }
        } else {
            target = requirePlayer(sender);
            if (target == null) {
                return;
            }
        }
        kits.give(name, target);
        plugin.messages().send(sender, "kits.given", "%kit%", name, "%player%", target.getName());
    }

    private void delete(CommandSender sender, KitModule kits, String[] args, String label) {
        if (args.length < 2) {
            plugin.messages().send(sender, "kits.delete-usage", "%label%", label);
            return;
        }
        if (!kits.delete(args[1])) {
            plugin.messages().send(sender, "kits.missing", "%kit%", args[1]);
            return;
        }
        plugin.messages().send(sender, "kits.deleted", "%kit%", args[1].toLowerCase(Locale.ROOT));
        plugin.guis().refreshAll();
    }

    private void list(CommandSender sender, KitModule kits) {
        List<String> names = kits.names();
        plugin.messages().send(sender, names.isEmpty() ? "kits.list-empty" : "kits.list",
                "%kits%", String.join(", ", names),
                "%count%", Integer.toString(names.size()));
    }

    @Override
    protected List<String> complete(CommandSender sender, String[] args) {
        KitModule kits = plugin.modules().byType(KitModule.class);
        if (kits == null) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(ACTIONS, args[0]);
        }
        if (args.length == 2) {
            return filter(args[0].equalsIgnoreCase("create") ? kits.suggestedNames() : kits.names(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> targets = new ArrayList<>(onlinePlayerNames());
            targets.add("all");
            return filter(targets, args[2]);
        }
        return Collections.emptyList();
    }
}

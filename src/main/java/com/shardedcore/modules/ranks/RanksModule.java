package com.shardedcore.modules.ranks;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Players;
import com.shardedcore.util.Tabs;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public final class RanksModule extends Module implements CommandExecutor, TabCompleter {

    public RanksModule(ShardedCore plugin) {
        super(plugin, "ranks");
    }

    @Override
    public void enable() {
        registerCommand("promote", this);
        registerCommand("demote", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean add = command.getName().equalsIgnoreCase("promote");
        if (!sender.hasPermission(add ? "shardedcore.promote" : "shardedcore.demote")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, add ? "usage-promote" : "usage-demote");
            return true;
        }
        OfflinePlayer target = Players.offline(args[0]);
        if (target == null || target.getUniqueId() == null) {
            send(sender, "player-missing");
            return true;
        }
        String group = args[1].toLowerCase(Locale.ROOT);
        LuckPerms api = Bukkit.getServicesManager().load(LuckPerms.class);
        if (api == null) {
            send(sender, "luckperms-missing");
            return true;
        }
        UUID uuid = target.getUniqueId();
        String name = Players.name(target);
        api.getUserManager().modifyUser(uuid, user -> setRole(user, group));
        send(sender, add ? "promoted" : "demoted", "player", name, "role", group);
        if (target.getPlayer() != null) {
            send(target.getPlayer(), add ? "promoted-self" : "demoted-self", "role", group);
        }
        return true;
    }

    private void setRole(User user, String group) {
        java.util.Set<String> replace = new java.util.HashSet<>();
        for (String name : config.getStringList("groups")) {
            if (name != null && !name.isBlank()) replace.add(name.toLowerCase(Locale.ROOT));
        }
        if (user.getPrimaryGroup() != null) replace.add(user.getPrimaryGroup().toLowerCase(Locale.ROOT));
        user.data().clear(node -> node instanceof InheritanceNode inheritance
                && replace.contains(inheritance.getGroupName().toLowerCase(Locale.ROOT)));
        user.data().add(InheritanceNode.builder(group).build());
        try {
            user.setPrimaryGroup(group);
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Tabs.players(args[0]);
        if (args.length == 2) return Tabs.filter(groups(), args[1]);
        return List.of();
    }

    private List<String> groups() {
        List<String> configured = new ArrayList<>(config.getStringList("groups"));
        LuckPerms api = Bukkit.getServicesManager().load(LuckPerms.class);
        if (api != null) {
            configured.addAll(api.getGroupManager().getLoadedGroups().stream()
                    .map(Group::getName)
                    .collect(Collectors.toList()));
        }
        return configured.stream().distinct().toList();
    }
}

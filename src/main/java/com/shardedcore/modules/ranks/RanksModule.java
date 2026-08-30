package com.shardedcore.modules.ranks;

import com.shardedcore.ShardedCore;
import com.shardedcore.database.Databases;
import com.shardedcore.database.Sqlite;
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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class RanksModule extends Module implements CommandExecutor, TabCompleter {

    private Sqlite sqlite;
    private boolean remoteSql;

    public RanksModule(ShardedCore plugin) {
        super(plugin, "ranks");
    }

    @Override
    public void enable() {
        sqlite = plugin.toggles().sqlite();
        Sqlite remote = Databases.open(plugin, config.getConfigurationSection("database"), sqlite, "Ranks");
        if (remote != sqlite) {
            sqlite = remote;
            remoteSql = true;
        }
        try {
            boolean mysql = sqlite.mysql();
            String id = mysql ? "INT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY AUTOINCREMENT";
            String text = mysql ? "VARCHAR(255)" : "TEXT";
            String integer = mysql ? "BIGINT" : "INTEGER";
            sqlite.run("CREATE TABLE IF NOT EXISTS rank_changes ("
                    + "id " + id + ", uuid " + text + " NOT NULL, name " + text + " NOT NULL, "
                    + "role " + text + " NOT NULL, action " + text + " NOT NULL, staff " + text + " NOT NULL, "
                    + "created " + integer + " NOT NULL)");
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to create rank_changes", ex);
        }
        registerCommand("promote", this);
        registerCommand("demote", this);
    }

    @Override
    public void disable() {
        if (remoteSql && sqlite != null) sqlite.close();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean promote = command.getName().equalsIgnoreCase("promote");
        if (!sender.hasPermission(promote ? "shardedcore.promote" : "shardedcore.demote")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, promote ? "usage-promote" : "usage-demote");
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
        log(uuid, name, group, promote ? "promote" : "demote", sender.getName());
        send(sender, promote ? "promoted" : "demoted", "player", name, "role", group);
        if (target.getPlayer() != null) {
            send(target.getPlayer(), promote ? "promoted-self" : "demoted-self", "role", group);
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

    private void log(UUID uuid, String name, String role, String action, String staff) {
        if (sqlite == null) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sqlite.execute("INSERT INTO rank_changes (uuid, name, role, action, staff, created) VALUES (?, ?, ?, ?, ?, ?)",
                        uuid.toString(), name, role, action, staff, System.currentTimeMillis());
            } catch (SQLException ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to log rank change", ex);
            }
        });
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

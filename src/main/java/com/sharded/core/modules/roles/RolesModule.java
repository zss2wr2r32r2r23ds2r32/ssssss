package com.sharded.core.modules.roles;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Apply LuckPerms role permission bundles (/role <rank> or /role <player> <rank>). */
public final class RolesModule extends Module implements CommandExecutor, TabCompleter {

    public RolesModule(ShardedCore plugin) {
        super(plugin, "roles");
    }

    @Override
    protected void onEnable() {
        registerCommand("role", this);
        registerCommand("roles", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            send(sender, "usage");
            return true;
        }

        Player target;
        String roleId;

        if (args.length >= 2 && sender.hasPermission("sharded.roles.admin")) {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                send(sender, "player-not-found", "%player%", args[0]);
                return true;
            }
            roleId = args[1].toLowerCase(Locale.ROOT);
        } else if (sender instanceof Player player) {
            if (!player.hasPermission("sharded.role.use") && !player.hasPermission("sharded.roles.admin")) {
                send(sender, "no-permission");
                return true;
            }
            target = player;
            roleId = args[0].toLowerCase(Locale.ROOT);
        } else {
            send(sender, "players-only");
            return true;
        }

        ConfigurationSection role = config.getConfigurationSection("roles." + roleId);
        if (role == null) {
            send(sender, "unknown-role", "%role%", roleId);
            return true;
        }
        if (!plugin.luckPerms().isAvailable()) {
            send(sender, "lp-missing");
            return true;
        }

        Set<String> perms = collectPermissions(roleId, role);
        String group = role.getString("luckperms-group", roleId);
        plugin.luckPerms().runConsole("lp user " + target.getName() + " parent set " + group);
        for (String perm : perms) {
            if (perm == null || perm.isBlank()) continue;
            plugin.luckPerms().runConsole("lp user " + target.getName() + " permission set " + perm + " true");
        }
        send(sender, "applied", "%player%", target.getName(), "%role%", roleId, "%count%", String.valueOf(perms.size()));
        if (!target.equals(sender)) {
            send(target, "received", "%role%", roleId);
        } else {
            send(target, "received-self", "%role%", roleId);
        }
        return true;
    }

    private Set<String> collectPermissions(String roleId, ConfigurationSection role) {
        Set<String> perms = new LinkedHashSet<>();
        if (role.getBoolean("grant-all", false)) {
            ConfigurationSection roles = config.getConfigurationSection("roles");
            if (roles != null) {
                for (String key : roles.getKeys(false)) {
                    ConfigurationSection r = roles.getConfigurationSection(key);
                    if (r != null) perms.addAll(r.getStringList("permissions"));
                }
            }
            perms.addAll(config.getStringList("staff-permissions"));
            ConfigurationSection all = config.getConfigurationSection("all-permissions");
            if (all != null) {
                for (String key : all.getKeys(false)) {
                    String perm = config.getString("all-permissions." + key);
                    if (perm != null && !perm.isBlank()) perms.add(perm);
                }
            }
        } else {
            perms.addAll(role.getStringList("permissions"));
            if (role.getBoolean("include-staff", false)) {
                perms.addAll(config.getStringList("staff-permissions"));
            }
            List<String> inherit = role.getStringList("inherit-permissions");
            for (String inheritRole : inherit) {
                ConfigurationSection inherited = config.getConfigurationSection("roles." + inheritRole);
                if (inherited != null) perms.addAll(inherited.getStringList("permissions"));
            }
        }
        return perms;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.role.use") && !sender.hasPermission("sharded.roles.admin")) {
            return List.of();
        }
        ConfigurationSection section = config.getConfigurationSection("roles");
        if (section == null) return List.of();
        List<String> roles = new ArrayList<>(section.getKeys(false));
        if (args.length == 1) {
            if (sender.hasPermission("sharded.roles.admin")) {
                List<String> combined = new ArrayList<>(TabCompleteHelper.onlinePlayers(args[0]));
                combined.addAll(TabCompleteHelper.filter(args[0], roles));
                return combined;
            }
            return TabCompleteHelper.filter(args[0], roles);
        }
        if (args.length == 2 && sender.hasPermission("sharded.roles.admin")) {
            return TabCompleteHelper.filter(args[1], roles);
        }
        return List.of();
    }
}

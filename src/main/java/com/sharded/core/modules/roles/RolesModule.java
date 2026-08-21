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

/** Apply LuckPerms role permission bundles (/roles). */
public final class RolesModule extends Module implements CommandExecutor, TabCompleter {

    public RolesModule(ShardedCore plugin) {
        super(plugin, "roles");
    }

    @Override
    protected void onEnable() {
        registerCommand("roles", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.roles.admin")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length < 2) {
            send(sender, "usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "player-not-found", "%player%", args[0]);
            return true;
        }
        String roleId = args[1].toLowerCase(Locale.ROOT);
        ConfigurationSection role = config.getConfigurationSection("roles." + roleId);
        if (role == null) {
            send(sender, "unknown-role", "%role%", roleId);
            return true;
        }
        if (!plugin.luckPerms().isAvailable()) {
            send(sender, "lp-missing");
            return true;
        }
        Set<String> perms = new LinkedHashSet<>();
        if (role.getBoolean("grant-all", false)) {
            ConfigurationSection all = config.getConfigurationSection("all-permissions");
            if (all != null) {
                for (String key : all.getKeys(false)) {
                    String perm = config.getString("all-permissions." + key);
                    if (perm != null) perms.add(perm);
                }
            }
            perms.add("sharded.*");
        }
        perms.addAll(role.getStringList("permissions"));
        if (role.getBoolean("include-staff", false)) {
            perms.addAll(config.getStringList("staff-permissions"));
        }
        String group = role.getString("luckperms-group", roleId);
        plugin.luckPerms().runConsole("lp user " + target.getName() + " parent set " + group);
        for (String perm : perms) {
            if (perm == null || perm.isBlank()) continue;
            plugin.luckPerms().runConsole("lp user " + target.getName() + " permission set " + perm + " true");
        }
        send(sender, "applied", "%player%", target.getName(), "%role%", roleId, "%count%", String.valueOf(perms.size()));
        send(target, "received", "%role%", roleId);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.roles.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.onlinePlayers(args[0]);
        if (args.length == 2) {
            ConfigurationSection section = config.getConfigurationSection("roles");
            if (section == null) return List.of();
            return TabCompleteHelper.filter(args[1], new ArrayList<>(section.getKeys(false)));
        }
        return List.of();
    }
}

package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /promote and /demote — sets the target LuckPerms group as parent + primary, then saves.
 * Uses LP parent set (not parent clear).
 */
public class RanksModule implements Module, Listener, CommandExecutor, TabCompleter {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;
    private List<String> roles;

    @Override
    public String getId() {
        return "ranks";
    }

    @Override
    public String getDisplayName() {
        return "Ranks";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.roles = config.getStringList("roles").stream()
                .map(r -> r.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(ArrayList::new));
        if (roles.isEmpty()) {
            roles.addAll(List.of(
                    "default", "helper", "sr-helper", "mod", "sr-mod",
                    "admin", "sr-admin", "manager", "developer", "director", "owner"
            ));
        }

        if (plugin.getCommand("promote") != null) {
            plugin.getCommand("promote").setExecutor(this);
            plugin.getCommand("promote").setTabCompleter(this);
        }
        if (plugin.getCommand("demote") != null) {
            plugin.getCommand("demote").setExecutor(this);
            plugin.getCommand("demote").setTabCompleter(this);
        }
    }

    @Override
    public void disable() {
        if (plugin.getCommand("promote") != null) {
            plugin.getCommand("promote").setExecutor(null);
            plugin.getCommand("promote").setTabCompleter(null);
        }
        if (plugin.getCommand("demote") != null) {
            plugin.getCommand("demote").setExecutor(null);
            plugin.getCommand("demote").setTabCompleter(null);
        }
        HandlerList.unregisterAll(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("shardedlobbycore.ranks.manage")) {
            MessageUtil.sendRaw(sender, config.getString("messages.no-permission",
                    "&#FF0000&lERROR &8▷ &fNo permission."));
            return true;
        }
        if (args.length < 2) {
            MessageUtil.sendRaw(sender, config.getString("messages.usage-" + command.getName(),
                    "&#FF0000&lERROR &8▷ &fUse &#FF0000/" + command.getName() + " <player> <role>&f."));
            return true;
        }

        String targetName = args[0];
        String role = args[1].toLowerCase(Locale.ROOT);
        if (!roles.contains(role)) {
            MessageUtil.sendRaw(sender, config.getString("messages.unknown-role",
                            "&#FF0000&lERROR &8▷ &fUnknown role &#FF0000%role%&f.")
                    .replace("%role%", role));
            return true;
        }

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null && !config.getBoolean("allow-offline", true)) {
            MessageUtil.sendRaw(sender, config.getString("messages.player-offline",
                            "&#FF0000&lERROR &8▷ &fPlayer &#FF0000%player% &fis not online.")
                    .replace("%player%", targetName));
            return true;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            MessageUtil.sendRaw(sender, config.getString("messages.no-luckperms",
                    "&#FF0000&lERROR &8▷ &fLuckPerms is not installed."));
            return true;
        }

        boolean promote = "promote".equalsIgnoreCase(command.getName());
        String playerArg = target != null ? target.getName() : targetName;
        applyRank(sender, playerArg, target, role, promote);
        return true;
    }

    private void applyRank(CommandSender sender, String playerName, Player online, String role, boolean promote) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LuckPerms api = LuckPermsProvider.get();

                Group group = resolveGroup(api, role);
                if (group == null) {
                    sync(() -> MessageUtil.sendRaw(sender, config.getString("messages.group-missing",
                                    "&#FF0000&lERROR &8▷ &fLuckPerms group &#FF0000%role% &fdoes not exist.")
                            .replace("%role%", role)));
                    return;
                }
                String groupName = group.getName();

                UUID uuid = resolveUuid(api, playerName, online);
                if (uuid == null) {
                    sync(() -> MessageUtil.sendRaw(sender, config.getString("messages.player-offline",
                                    "&#FF0000&lERROR &8▷ &fPlayer &#FF0000%player% &fis not online.")
                            .replace("%player%", playerName)));
                    return;
                }

                User user = api.getUserManager().loadUser(uuid).join();
                if (user == null) {
                    sync(() -> MessageUtil.sendRaw(sender, config.getString("messages.failed",
                            "&#FF0000&lERROR &8▷ &fFailed to update rank. Check console.")));
                    return;
                }

                // parent set semantics: ensure membership + primary. Do not parent clear.
                user.data().add(InheritanceNode.builder(groupName).build());

                var primaryResult = user.setPrimaryGroup(groupName);
                if (!primaryResult.wasSuccessful()) {
                    plugin.getLogger().warning("setPrimaryGroup(" + groupName + ") for "
                            + playerName + " returned " + primaryResult);
                }

                api.getUserManager().saveUser(user).join();
                user.getCachedData().invalidate();
                api.getMessagingService().ifPresent(ms -> ms.pushUserUpdate(user));

                String effective = user.getPrimaryGroup();
                boolean mismatch = effective != null && !effective.equalsIgnoreCase(groupName);
                boolean alsoConsole = config.getBoolean("also-run-console-commands", true);

                sync(() -> {
                    if (alsoConsole) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                "lp user " + playerName + " parent set " + groupName);
                    }

                    if (mismatch) {
                        MessageUtil.sendRaw(sender, config.getString("messages.primary-mismatch",
                                        "&#FFB600&lRANKS &8▷ &fSet &#FFB600%role% &fbut LuckPerms primary is &#FFB600%primary%&f. "
                                                + "Check group weights / primary-group-calculation.")
                                .replace("%role%", groupName)
                                .replace("%primary%", effective));
                        plugin.getLogger().warning("Rank set to " + groupName + " for " + playerName
                                + " but effective primary group is " + effective
                                + ". Ensure the " + groupName + " group exists with a higher weight than "
                                + effective + ", or set primary-group-calculation: stored in LuckPerms config.");
                    }

                    String path = promote ? "messages.promoted" : "messages.demoted";
                    String fallback = promote
                            ? "&#94FF00&lRANKS &8▷ &fPromoted &#94FF00%player% &fto &#94FF00%role%&f."
                            : "&#FFB600&lRANKS &8▷ &fDemoted &#FFB600%player% &fto &#FFB600%role%&f.";
                    MessageUtil.sendRaw(sender, config.getString(path, fallback)
                            .replace("%player%", playerName)
                            .replace("%role%", groupName));

                    if (online != null && online.isOnline() && config.getBoolean("notify-target", true)) {
                        String targetMsg = config.getString(
                                        "messages.target-" + (promote ? "promoted" : "demoted"),
                                        "&#94FF00&lRANKS &8▷ &fYour rank is now &#94FF00%role%&f.")
                                .replace("%role%", groupName);
                        MessageUtil.sendFormatted(online, targetMsg);
                    }
                });
            } catch (Exception ex) {
                plugin.getLogger().warning("Rank update failed for " + playerName + ": " + ex.getMessage());
                sync(() -> MessageUtil.sendRaw(sender, config.getString("messages.failed",
                        "&#FF0000&lERROR &8▷ &fFailed to update rank. Check console.")));
            }
        });
    }

    private Group resolveGroup(LuckPerms api, String role) {
        Group group = api.getGroupManager().getGroup(role);
        if (group != null) {
            return group;
        }
        for (Group loaded : api.getGroupManager().getLoadedGroups()) {
            if (loaded.getName().equalsIgnoreCase(role)) {
                return loaded;
            }
        }
        return null;
    }

    private UUID resolveUuid(LuckPerms api, String playerName, Player online) {
        if (online != null) {
            return online.getUniqueId();
        }
        try {
            return api.getUserManager().lookupUniqueId(playerName).join();
        } catch (Exception ex) {
            plugin.getLogger().warning("UUID lookup failed for " + playerName + ": " + ex.getMessage());
            return null;
        }
    }

    private void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("shardedlobbycore.ranks.manage")) {
            return List.of();
        }
        if (args.length == 1) {
            String partial = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(partial))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase(Locale.ROOT);
            return roles.stream()
                    .filter(role -> role.startsWith(partial))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

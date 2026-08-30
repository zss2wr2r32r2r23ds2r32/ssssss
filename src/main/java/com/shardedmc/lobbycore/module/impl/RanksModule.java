package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * /promote and /demote using LuckPerms primary group changes.
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
            roles.addAll(List.of("default", "vip", "mvp", "helper", "mod", "admin", "owner"));
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
        if (target == null) {
            MessageUtil.sendRaw(sender, config.getString("messages.player-offline",
                            "&#FF0000&lERROR &8▷ &fPlayer &#FF0000%player% &fis not online.")
                    .replace("%player%", targetName));
            return true;
        }

        boolean promote = "promote".equalsIgnoreCase(command.getName());
        setPrimaryGroup(sender, target, role, promote);
        return true;
    }

    private void setPrimaryGroup(CommandSender sender, Player target, String role, boolean promote) {
        if (!Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            MessageUtil.sendRaw(sender, config.getString("messages.no-luckperms",
                    "&#FF0000&lERROR &8▷ &fLuckPerms is not installed."));
            return;
        }

        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object groupManager = api.getClass().getMethod("getGroupManager").invoke(api);

            Object group = groupManager.getClass().getMethod("getGroup", String.class).invoke(groupManager, role);
            if (group == null) {
                MessageUtil.sendRaw(sender, config.getString("messages.group-missing",
                                "&#FF0000&lERROR &8▷ &fLuckPerms group &#FF0000%role% &fdoes not exist.")
                        .replace("%role%", role));
                return;
            }

            @SuppressWarnings("unchecked")
            CompletableFuture<Object> future = (CompletableFuture<Object>) userManager.getClass()
                    .getMethod("loadUser", java.util.UUID.class)
                    .invoke(userManager, target.getUniqueId());

            future.thenAccept(user -> {
                try {
                    Object data = user.getClass().getMethod("data").invoke(user);

                    // Set primary group
                    user.getClass().getMethod("setPrimaryGroup", String.class).invoke(user, role);

                    // Ensure they inherit the group
                    Class<?> inheritanceNode = Class.forName("net.luckperms.api.node.types.InheritanceNode");
                    Object builder = inheritanceNode.getMethod("builder", String.class).invoke(null, role);
                    Object node = builder.getClass().getMethod("build").invoke(builder);
                    data.getClass().getMethod("add", Class.forName("net.luckperms.api.node.Node")).invoke(data, node);

                    userManager.getClass().getMethod("saveUser", Class.forName("net.luckperms.api.model.user.User"))
                            .invoke(userManager, user);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String path = promote ? "messages.promoted" : "messages.demoted";
                        String fallback = promote
                                ? "&#94FF00&lRANKS &8▷ &fPromoted &#94FF00%player% &fto &#94FF00%role%&f."
                                : "&#FFB600&lRANKS &8▷ &fDemoted &#FFB600%player% &fto &#FFB600%role%&f.";
                        MessageUtil.sendRaw(sender, config.getString(path, fallback)
                                .replace("%player%", target.getName())
                                .replace("%role%", role));
                        if (config.getBoolean("notify-target", true)) {
                            String targetMsg = config.getString("messages.target-" + (promote ? "promoted" : "demoted"),
                                            "&#94FF00&lRANKS &8▷ &fYour rank is now &#94FF00%role%&f.")
                                    .replace("%role%", role);
                            MessageUtil.sendFormatted(target, targetMsg);
                        }
                    });
                } catch (ReflectiveOperationException ex) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            MessageUtil.sendRaw(sender, "&#FF0000&lERROR &8▷ &fFailed to update rank: " + ex.getMessage()));
                    plugin.getLogger().warning("Rank update failed: " + ex.getMessage());
                }
            }).exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                        MessageUtil.sendRaw(sender, "&#FF0000&lERROR &8▷ &fFailed to load LuckPerms user."));
                return null;
            });
        } catch (ReflectiveOperationException ex) {
            MessageUtil.sendRaw(sender, "&#FF0000&lERROR &8▷ &fLuckPerms API error: " + ex.getMessage());
            plugin.getLogger().warning("LuckPerms promote/demote error: " + ex.getMessage());
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
                    .sorted()
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

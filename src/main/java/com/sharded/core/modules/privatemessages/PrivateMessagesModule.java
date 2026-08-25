package com.sharded.core.modules.privatemessages;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /msg, /reply and /msgtoggle. Players can toggle receiving private messages;
 * staff with sharded.msg.bypass can always message them.
 */
public final class PrivateMessagesModule extends Module implements CommandExecutor, TabCompleter {

    public static final String STATE_KEY = "msg-enabled";

    private final Map<UUID, UUID> lastConversation = new HashMap<>();

    public PrivateMessagesModule(ShardedCore plugin) {
        super(plugin, "privatemessages");
    }

    @Override
    protected void onEnable() {
        registerCommand("msg", this);
        registerCommand("reply", this);
        registerCommand("msgtoggle", this);
        com.sharded.core.util.CommandOverride.takeOver(plugin, "msgtoggle", this, this);
        com.sharded.core.util.CommandOverride.takeOver(plugin, "togglemsg", this, this);
        com.sharded.core.util.CommandOverride.takeOver(plugin, "pmtoggle", this, this);
    }

    @Override
    protected void onDisable() {
        lastConversation.clear();
    }

    public boolean isMsgEnabled(Player player) {
        return plugin.stateStore().getBool(player.getUniqueId(), STATE_KEY, true);
    }

    public void setMsgEnabled(Player player, boolean enabled) {
        plugin.stateStore().setBool(player.getUniqueId(), STATE_KEY, enabled);
        send(player, enabled ? "msg-enabled" : "msg-disabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "msgtoggle", "togglemsg", "pmtoggle" -> {
                if (!player.hasPermission("sharded.msg.toggle")) {
                    send(player, "no-permission");
                    return true;
                }
                setMsgEnabled(player, !isMsgEnabled(player));
            }
            case "msg" -> {
                if (!player.hasPermission("sharded.msg.use")) {
                    send(player, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    send(player, "msg-usage");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    send(player, "player-not-found", "%player%", args[0]);
                    return true;
                }
                deliver(player, target, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            }
            case "reply" -> {
                if (!player.hasPermission("sharded.msg.use")) {
                    send(player, "no-permission");
                    return true;
                }
                if (args.length < 1) {
                    send(player, "reply-usage");
                    return true;
                }
                UUID lastId = lastConversation.get(player.getUniqueId());
                Player target = lastId == null ? null : Bukkit.getPlayer(lastId);
                if (target == null || !target.isOnline()) {
                    send(player, "nobody-to-reply");
                    return true;
                }
                deliver(player, target, String.join(" ", args));
            }
        }
        return true;
    }

    private void deliver(Player from, Player to, String message) {
        if (from.equals(to)) {
            send(from, "cannot-message-self");
            return;
        }
        if (!isMsgEnabled(to) && !from.hasPermission("sharded.msg.bypass")) {
            send(from, "target-has-msg-disabled", "%player%", to.getName());
            return;
        }
        send(from, "format-sent", "%player%", to.getName(), "%message%", message);
        send(to, "format-received", "%player%", from.getName(), "%message%", message);
        if (config.getBoolean("play-sound", true)) {
            to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
        }
        lastConversation.put(from.getUniqueId(), to.getUniqueId());
        lastConversation.put(to.getUniqueId(), from.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastConversation.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("msg") && args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[0].toLowerCase())) names.add(p.getName());
            }
            return names;
        }
        return List.of();
    }
}

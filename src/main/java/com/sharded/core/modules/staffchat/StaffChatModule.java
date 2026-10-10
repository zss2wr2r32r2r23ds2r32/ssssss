package com.sharded.core.modules.staffchat;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Staff-only chat channel with toggle mode. */
public final class StaffChatModule extends Module implements CommandExecutor {

    private final Set<UUID> toggleMode = ConcurrentHashMap.newKeySet();

    public StaffChatModule(ShardedCore plugin) {
        super(plugin, "staffchat");
    }

    public boolean isStaffChatMode(UUID uuid) {
        return toggleMode.contains(uuid);
    }

    /** Enables or disables staff chat toggle mode. */
    public void setEnabled(Player player, boolean enabled, boolean notify) {
        if (enabled) toggleMode.add(player.getUniqueId());
        else toggleMode.remove(player.getUniqueId());
        if (notify) send(player, enabled ? "enabled" : "disabled");
    }

    public boolean isLockedByStaffMode(Player player) {
        var staff = plugin.modules().get(com.sharded.core.modules.staff.StaffModule.class);
        return staff != null && staff.staffMode() != null && staff.staffMode().isStaffMode(player.getUniqueId());
    }

    @Override
    protected void onEnable() {
        registerListener(this);
        registerCommand("staffchat", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String perm = config.getString("permission", "sharded.staffchat.use");
        if (!sender.hasPermission(perm)) {
            send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length > 0) {
            broadcast(player.getName(), String.join(" ", args));
            return true;
        }
        if (toggleMode.contains(player.getUniqueId())) {
            if (isLockedByStaffMode(player)) {
                send(player, "staffmode-locked");
                return true;
            }
            setEnabled(player, false, true);
        } else {
            setEnabled(player, true, true);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!toggleMode.contains(player.getUniqueId())) return;
        String perm = config.getString("permission", "sharded.staffchat.use");
        if (!player.hasPermission(perm)) {
            toggleMode.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (message.isBlank()) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> broadcast(player.getName(), message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        toggleMode.remove(event.getPlayer().getUniqueId());
    }

    private void broadcast(String playerName, String message) {
        String perm = config.getString("permission", "sharded.staffchat.use");
        String format = config.getString("format", messages.getString("format",
                "&#AD4EFF&lSTAFF &8▷ &f%player%&7: &f%message%"));
        String formatted = Text.apply(format.replace("%prefix%", messagePrefix()),
                "%player%", playerName, "%message%", message);
        var component = Text.c(formatted);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(perm)) online.sendMessage(component);
        }
    }
}

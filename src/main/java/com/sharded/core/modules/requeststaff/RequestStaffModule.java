package com.sharded.core.modules.requeststaff;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Players request staff help with clickable teleport for staff. */
public final class RequestStaffModule extends Module implements CommandExecutor {

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> claimedBy = new ConcurrentHashMap<>();

    public RequestStaffModule(ShardedCore plugin) {
        super(plugin, "requeststaff");
    }

    @Override
    protected void onEnable() {
        registerCommand("requeststaff", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player staff)) {
                send(sender, "players-only");
                return true;
            }
            handleStaffTeleport(staff, args[1]);
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.requeststaff.use")) {
            send(player, "no-permission");
            return true;
        }
        long cooldownMs = config.getLong("cooldown-seconds", 30L) * 1000L;
        long now = System.currentTimeMillis();
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            send(player, "cooldown", "%time%", String.valueOf((cooldownMs - (now - last)) / 1000L + 1));
            return true;
        }
        cooldowns.put(player.getUniqueId(), now);
        claimedBy.remove(player.getUniqueId());

        String staffPerm = config.getString("staff-permission", "sharded.staff");
        String alert = raw("staff-alert", "%player%", player.getName());
        var base = Text.c(alert);
        var clickable = base.append(Text.c(raw("click-here")).clickEvent(ClickEvent.runCommand("/requeststaff tp " + player.getName()))
                .hoverEvent(HoverEvent.showText(Text.c(raw("click-hover")))));

        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission(staffPerm)) staff.sendMessage(clickable);
        }
        send(player, "sent");
        return true;
    }

    public boolean handleStaffTeleport(Player staff, String targetName) {
        if (!staff.hasPermission(config.getString("staff-permission", "sharded.staff"))) return false;
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            send(staff, "target-offline");
            return true;
        }
        UUID claim = claimedBy.get(target.getUniqueId());
        if (claim != null && !claim.equals(staff.getUniqueId())) {
            send(staff, "already-helping");
            return true;
        }
        claimedBy.put(target.getUniqueId(), staff.getUniqueId());
        staff.teleport(target.getLocation());
        send(staff, "teleported", "%player%", target.getName());
        send(target, "staff-coming", "%staff%", staff.getName());
        return true;
    }
}

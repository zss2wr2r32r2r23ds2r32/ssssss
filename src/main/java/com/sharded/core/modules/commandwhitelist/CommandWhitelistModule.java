package com.sharded.core.modules.commandwhitelist;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Restricts which commands non-op players can see and use. */
public final class CommandWhitelistModule extends Module implements Listener {

    private Set<String> whitelist = Set.of();
    private Set<String> alwaysAllowed = Set.of();

    public CommandWhitelistModule(ShardedCore plugin) {
        super(plugin, "commandwhitelist");
    }

    @Override
    protected void onEnable() {
        reloadLists();
        registerListener(this);
    }

    public void reloadLists() {
        whitelist = normalize(config.getStringList("commands"));
        alwaysAllowed = normalize(config.getStringList("always-allowed"));
    }

    private Set<String> normalize(List<String> raw) {
        Set<String> out = new HashSet<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) continue;
            String cmd = entry.trim().toLowerCase(Locale.ROOT);
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            int space = cmd.indexOf(' ');
            if (space > 0) cmd = cmd.substring(0, space);
            out.add(cmd);
        }
        return out;
    }

    private boolean canBypass(Player player) {
        if (!config.getBoolean("enabled", true)) return true;
        if (player.isOp() && config.getBoolean("ops-bypass", true)) return true;
        return player.hasPermission("sharded.commandwhitelist.bypass");
    }

    private boolean isAllowed(String label) {
        String cmd = label.toLowerCase(Locale.ROOT);
        if (alwaysAllowed.contains(cmd)) return true;
        if (whitelist.contains(cmd)) return true;
        for (String allowed : whitelist) {
            if (cmd.equals(allowed)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        String message = event.getMessage();
        if (!message.startsWith("/")) return;
        String body = message.substring(1);
        int space = body.indexOf(' ');
        String label = space > 0 ? body.substring(0, space) : body;
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (isAllowed(label)) return;
        event.setCancelled(true);
        send(player, "blocked", "%command%", label);
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (canBypass(player)) return;
        event.getCommands().removeIf(label -> !isAllowed(label));
    }
}

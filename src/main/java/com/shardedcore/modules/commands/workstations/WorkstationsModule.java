package com.shardedcore.modules.commands.workstations;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class WorkstationsModule extends Module implements CommandExecutor {

    public WorkstationsModule(ShardedCore plugin) {
        super(plugin, "workstations");
    }

    @Override
    public void enable() {
        registerCommand("anvil", this);
        registerCommand("grindstone", this);
        registerCommand("smithingtable", this);
        registerCommand("craft", this);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        String permission = config.getString("permissions." + cmd, "shardedcore.command." + cmd);
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            send(player, "no-permission");
            return true;
        }
        switch (cmd) {
            case "anvil" -> player.openAnvil(null, true);
            case "grindstone" -> player.openGrindstone(null, true);
            case "smithingtable" -> player.openSmithingTable(null, true);
            case "craft" -> player.openWorkbench(null, true);
            default -> { return true; }
        }
        if (config.getBoolean("play-sound", true)) {
            String soundName = config.getString("sounds." + cmd, "");
            if (soundName != null && !soundName.isBlank()) {
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase(Locale.ROOT)), 0.6f, 1.2f);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return true;
    }
}

package com.sharded.core.modules.workstations;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.craft.CraftModule;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Portable anvil, grindstone, smithing table, and crafting table commands. */
public final class WorkstationsModule extends Module implements CommandExecutor {

    public WorkstationsModule(ShardedCore plugin) {
        super(plugin, "workstations");
    }

    @Override
    protected void onEnable() {
        registerCommand("anvil", this);
        registerCommand("grindstone", this);
        registerCommand("smithingtable", this);
        if (!craftHandledElsewhere()) {
            registerCommand("craft", this);
        }
    }

    private boolean craftHandledElsewhere() {
        CraftModule craft = plugin.modules().get(CraftModule.class);
        return craft != null && craft.isEnabled()
                && plugin.modules().isConfiguredEnabled("craft")
                && !config.getBoolean("override-craft", false);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        String permission = config.getString("permissions." + cmd,
                "sharded.workstations." + cmd);
        if (!permission.isBlank() && !player.hasPermission(permission)) {
            send(player, "no-permission");
            return true;
        }
        switch (cmd) {
            case "anvil" -> player.openAnvil(null, true);
            case "grindstone" -> player.openGrindstone(null, true);
            case "smithingtable" -> player.openSmithingTable(null, true);
            case "craft", "workbench", "wb" -> player.openWorkbench(null, true);
            default -> {
                return true;
            }
        }
        playSound(player, cmd);
        return true;
    }

    private void playSound(Player player, String cmd) {
        String soundName = config.getString("sounds." + cmd, "");
        if (soundName == null || soundName.isBlank()) return;
        if (!config.getBoolean("play-sound", true)) return;
        try {
            Sound sound = Sound.valueOf(soundName.trim().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 0.6f, 1.2f);
        } catch (IllegalArgumentException ignored) {
        }
    }
}

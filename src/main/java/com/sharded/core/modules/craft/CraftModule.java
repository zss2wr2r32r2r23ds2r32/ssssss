package com.sharded.core.modules.craft;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CraftModule extends Module implements CommandExecutor {

    public CraftModule(ShardedCore plugin) {
        super(plugin, "craft");
    }

    @Override
    protected void onEnable() {
        registerCommand("craft", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.craft.use")) {
            send(player, "no-permission");
            return true;
        }
        player.openWorkbench(null, true);
        if (config.getBoolean("play-sound", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.6f, 1.2f);
        }
        send(player, "opened");
        return true;
    }
}

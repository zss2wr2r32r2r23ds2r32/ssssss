package com.sharded.core.modules.crates;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/** Crate preview/open hub (/crates). */
public final class CratesModule extends Module implements CommandExecutor {

    public CratesModule(ShardedCore plugin) {
        super(plugin, "crates");
    }

    @Override
    protected void onEnable() {
        syncJarResource("gui.yml");
        plugin.gui().loadMenu(new File(moduleFolder(), "gui.yml"), "crates");
        registerCommand("crates", this);
        registerCommand("crate", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.crates.use")) {
            send(player, "no-permission");
            return true;
        }
        plugin.gui().open(player, "crates");
        return true;
    }
}

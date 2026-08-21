package com.sharded.core.modules.media;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/** Media requirements GUI (/media). */
public final class MediaModule extends Module implements CommandExecutor {

    public MediaModule(ShardedCore plugin) {
        super(plugin, "media");
    }

    @Override
    protected void onEnable() {
        syncJarResource("gui.yml");
        plugin.gui().loadMenu(new File(moduleFolder(), "gui.yml"), "media");
        registerCommand("media", this);
        registerCommand("mediareq", this);
        registerCommand("mediarequirements", this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.media.use")) {
            send(player, "no-permission");
            return true;
        }
        plugin.gui().open(player, "media");
        return true;
    }
}

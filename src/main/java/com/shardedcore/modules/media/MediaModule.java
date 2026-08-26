package com.shardedcore.modules.media;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ConfigSync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

public final class MediaModule extends Module implements CommandExecutor {

    public MediaModule(ShardedCore plugin) {
        super(plugin, "media");
    }

    @Override
    public void enable() {
        File guiFile = new File(moduleFolder, "gui.yml");
        ConfigSync.sync(plugin, guiFile, "modules/media/gui.yml");
        plugin.gui().loadMenu(guiFile, "media");
        registerCommand("media", this);
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
        if (!player.hasPermission("sharded.media.use") && !player.hasPermission("shardedcore.command.media")) {
            send(player, "no-permission");
            return true;
        }
        plugin.gui().open(player, "media");
        return true;
    }
}

package com.shardedcore.modules.media;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.event.Listener;
import com.shardedcore.util.ConfigUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.io.File;

public final class MediaModule extends Module implements Listener, CommandExecutor {

    private MediaGuiHandler guiHandler;

    public MediaModule(ShardedCore plugin) { super(plugin, "media"); }

    @Override
    public void enable() {
        ConfigUtil.saveDefaultResource(plugin, "modules/media/gui.yml", new File(moduleFolder, "gui.yml"), false);
        guiHandler = new MediaGuiHandler(this);
        registerListener(this);
        registerCommand("media", this);
    }

    @Override public void disable() { guiHandler = null; cleanup(); }

    File guiFile() { return new File(moduleFolder, "gui.yml"); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { send(sender, "players-only"); return true; }
        if (!player.hasPermission("shardedcore.command.media")) { send(player, "no-permission"); return true; }
        guiHandler.open(player);
        return true;
    }

    @EventHandler public void onClick(InventoryClickEvent event) { guiHandler.handleClick(event); }
}

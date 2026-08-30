package com.shardedmc.lobbycore.module.impl;

import com.shardedmc.lobbycore.ShardedLobbyCore;
import com.shardedmc.lobbycore.module.Module;
import com.shardedmc.lobbycore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SpawnModule implements Module, CommandExecutor {

    private ShardedLobbyCore plugin;
    private FileConfiguration config;

    @Override
    public String getId() {
        return "spawn";
    }

    @Override
    public String getDisplayName() {
        return "Spawn";
    }

    @Override
    public void enable(ShardedLobbyCore plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        if (plugin.getCommand("setspawn") != null) {
            plugin.getCommand("setspawn").setExecutor(this);
        }
    }

    @Override
    public void disable() {
        if (plugin.getCommand("setspawn") != null) {
            plugin.getCommand("setspawn").setExecutor(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.send(sender, "player-only");
            return true;
        }

        if (!player.hasPermission("shardedlobbycore.setspawn")) {
            MessageUtil.send(sender, "no-permission");
            return true;
        }

        plugin.getSpawnManager().setSpawn(player.getLocation());
        MessageUtil.sendFormatted(player, config.getString("messages.set", "&aSpawn point set!"));
        return true;
    }
}

package com.shardedcore.modules.playtimerewards;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.SQLException;

/** Milestone GUI rewards based on play time statistic. */
public final class PlaytimeRewardsModule extends Module implements CommandExecutor {

    private PlaytimeRewardsDatabase database;
    private PlaytimeRewardsGuiHandler guiHandler;

    public PlaytimeRewardsModule(ShardedCore plugin) {
        super(plugin, "playtime-rewards");
    }

    @Override
    public void enable() {
        try {
            database = new PlaytimeRewardsDatabase(plugin, moduleFolder);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open playtime rewards database", e);
        }
        guiHandler = new PlaytimeRewardsGuiHandler(this);
        registerListener(guiHandler);
        registerCommand("playtimerewards", this);
    }

    @Override
    public void disable() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    PlaytimeRewardsDatabase database() {
        return database;
    }

    org.bukkit.configuration.file.FileConfiguration rewardConfig() {
        return config;
    }

    ShardedCore plugin() {
        return plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command.playtimerewards")) {
            send(player, "no-permission");
            return true;
        }
        guiHandler.open(player);
        return true;
    }
}

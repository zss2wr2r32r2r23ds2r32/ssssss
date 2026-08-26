package com.shardedcore.modules.killrewards;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.sql.SQLException;

/** Milestone GUI rewards based on player kill statistic. */
public final class KillRewardsModule extends Module implements CommandExecutor {

    private KillRewardsDatabase database;
    private KillRewardsGuiHandler guiHandler;

    public KillRewardsModule(ShardedCore plugin) {
        super(plugin, "kill-rewards");
    }

    @Override
    public void enable() {
        try {
            database = new KillRewardsDatabase(plugin, moduleFolder);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open kill rewards database", e);
        }
        guiHandler = new KillRewardsGuiHandler(this);
        registerListener(guiHandler);
        registerCommand("killrewards", this);
    }

    @Override
    public void disable() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    KillRewardsDatabase database() {
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
        if (!player.hasPermission("shardedcore.command.killrewards")) {
            send(player, "no-permission");
            return true;
        }
        guiHandler.open(player);
        return true;
    }
}

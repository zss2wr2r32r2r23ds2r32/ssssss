package com.sharded.core.modules.playtimerewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
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
        super(plugin, "playtimerewards");
    }

    @Override
    protected void onEnable() {
        try {
            database = new PlaytimeRewardsDatabase(plugin, moduleFolder());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open playtime rewards database", e);
        }
        guiHandler = new PlaytimeRewardsGuiHandler(this);
        registerListener(guiHandler);
        registerCommand("playtimerewards", this);
    }

    @Override
    protected void onDisable() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    PlaytimeRewardsDatabase database() {
        return database;
    }

    YamlConfiguration config() {
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
        if (!player.hasPermission("sharded.playtimerewards.use")) {
            send(player, "no-permission");
            return true;
        }
        guiHandler.open(player);
        return true;
    }
}

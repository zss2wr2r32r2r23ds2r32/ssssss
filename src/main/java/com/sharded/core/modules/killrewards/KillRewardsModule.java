package com.sharded.core.modules.killrewards;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
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
        super(plugin, "killrewards");
    }

    @Override
    protected void onEnable() {
        try {
            database = new KillRewardsDatabase(plugin, moduleFolder());
        } catch (SQLException e) {
            throw new IllegalStateException("Could not open kill rewards database", e);
        }
        guiHandler = new KillRewardsGuiHandler(this);
        registerListener(guiHandler);
        registerCommand("killrewards", this);
    }

    @Override
    protected void onDisable() {
        if (database != null) {
            database.close();
            database = null;
        }
    }

    KillRewardsDatabase database() {
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
        if (!player.hasPermission("sharded.killrewards.use")) {
            send(player, "no-permission");
            return true;
        }
        guiHandler.open(player);
        return true;
    }
}

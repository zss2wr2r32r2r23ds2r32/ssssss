package dev.sharded.velocitycore.backend;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class BackendPlugin extends JavaPlugin {

    private String serverName;
    private int reportIntervalTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();

        getServer().getMessenger().registerOutgoingPluginChannel(this, WhitelistMessages.CHANNEL);

        long interval = Math.max(1, reportIntervalTicks);
        Bukkit.getScheduler().runTaskTimer(this, this::reportWhitelistStatus, interval, interval);

        reportWhitelistStatus();
        getLogger().info("Reporting whitelist status for '" + serverName + "' to Velocity.");
    }

    private void reloadLocalConfig() {
        serverName = getConfig().getString("server-name", "survival").toLowerCase();
        reportIntervalTicks = getConfig().getInt("report-interval-ticks", 20);
    }

    private void reportWhitelistStatus() {
        if (!getServer().getMessenger().isOutgoingChannelRegistered(this, WhitelistMessages.CHANNEL)) {
            return;
        }

        boolean whitelisted = Bukkit.hasWhitelist();
        byte[] payload = WhitelistMessages.encode(serverName, whitelisted);
        getServer().sendPluginMessage(this, WhitelistMessages.CHANNEL, payload);
    }
}

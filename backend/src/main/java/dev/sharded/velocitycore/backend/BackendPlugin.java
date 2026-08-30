package dev.sharded.velocitycore.backend;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class BackendPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    private String serverName;
    private int reportIntervalTicks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocalConfig();

        getServer().getMessenger().registerOutgoingPluginChannel(this, WhitelistMessages.CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, WhitelistMessages.CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);

        long interval = Math.max(1, reportIntervalTicks);
        Bukkit.getScheduler().runTaskTimer(this, this::reportWhitelistStatus, interval, interval);

        reportWhitelistStatus();
        getLogger().info("Reporting whitelist status for '" + serverName + "' to Velocity.");
    }

    @Override
    public void onPluginMessageReceived(String channel, org.bukkit.entity.Player player, byte[] message) {
        if (!channel.equals(WhitelistMessages.CHANNEL)) {
            return;
        }
        if (WhitelistMessages.isRequest(message)) {
            reportWhitelistStatus();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        reportWhitelistStatus();
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

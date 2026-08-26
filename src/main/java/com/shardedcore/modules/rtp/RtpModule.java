package com.shardedcore.modules.rtp;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import org.bukkit.event.Listener;
import com.shardedcore.modules.economy.EconomyModule;
import com.shardedcore.util.ConfigUtil;
import com.shardedcore.util.MessageUtil;
import com.shardedcore.util.SafeLocationFinder;
import com.shardedcore.util.TeleportHelper;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class RtpModule extends Module implements Listener, CommandExecutor {

    private RtpGuiHandler guiHandler;
    private RtpSafeSpotPool safeSpotPool;
    private TeleportHelper teleportHelper;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public RtpModule(ShardedCore plugin) { super(plugin, "rtp"); }

    @Override
    public void enable() {
        ConfigUtil.saveDefaultResource(plugin, "modules/rtp/gui.yml", new File(moduleFolder, "gui.yml"), false);
        guiHandler = new RtpGuiHandler(this);
        safeSpotPool = new RtpSafeSpotPool(this);
        safeSpotPool.start();
        teleportHelper = new TeleportHelper(plugin);
        teleportHelper.start();
        registerListener(this);
        registerCommand("rtp", this);
    }

    @Override
    public void disable() {
        if (safeSpotPool != null) safeSpotPool.shutdown();
        if (teleportHelper != null) teleportHelper.shutdown();
        cooldowns.clear();
        cleanup();
    }

    ConfigurationSection destination(String id) { return config.getConfigurationSection("destinations." + id); }
    org.bukkit.configuration.file.FileConfiguration rtpConfig() { return config; }
    ShardedCore plugin() { return plugin; }
    File moduleFolderPath() { return moduleFolder; }

    void beginTeleport(Player player, String destinationId) {
        ConfigurationSection dest = destination(destinationId);
        if (dest == null) { send(player, "invalid-destination"); return; }
        long cooldownMs = config.getLong("cooldown-seconds", 30L) * 1000L;
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && System.currentTimeMillis() - last < cooldownMs) {
            send(player, "cooldown", "seconds", String.valueOf((cooldownMs - (System.currentTimeMillis() - last)) / 1000L));
            return;
        }
        long cost = config.getLong("cost", 0L);
        EconomyModule economy = plugin.modules().get(EconomyModule.class);
        if (cost > 0 && economy != null && economy.service().getBalance(player.getUniqueId()) < cost) {
            send(player, "insufficient-funds", "cost", String.valueOf(cost));
            return;
        }
        World world = Bukkit.getWorld(dest.getString("world", "world"));
        if (world == null) { send(player, "world-not-found", "world", dest.getString("world", "world")); return; }
        Location target = safeSpotPool.poll(destinationId, world);
        if (target == null) target = SafeLocationFinder.find(world, config);
        if (target == null) { send(player, "no-safe-location"); return; }
        int delay = config.getInt("countdown-seconds", 5);
        String countdown = dest.getString("countdown-actionbar", config.getString("countdown-actionbar", "&#97F900&lRTP &8▷ &#97F900&n{seconds}s"));
        String cancelled = config.getString("teleport-cancelled-actionbar", "&#97F900&lRTP &8▷ &7Cancelled.");
        player.closeInventory();
        teleportHelper.teleportDelayed(player, target, delay, countdown, p -> {
            if (cost > 0 && economy != null) economy.service().take(p.getUniqueId(), cost);
            cooldowns.put(p.getUniqueId(), System.currentTimeMillis());
            String success = dest.getString("success-actionbar", "");
            if (success != null && !success.isBlank()) MessageUtil.sendActionBar(p, plugin, success);
            send(p, "teleported", "destination", dest.getString("display-name", destinationId));
        }, () -> MessageUtil.sendActionBar(player, plugin, cancelled));
    }

    @EventHandler public void onGuiClick(InventoryClickEvent event) { guiHandler.handleClick(event); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { if (teleportHelper != null) teleportHelper.cancel(event.getPlayer().getUniqueId()); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { send(sender, "players-only"); return true; }
        if (!player.hasPermission("shardedcore.command.rtp")) { send(player, "no-permission"); return true; }
        guiHandler.open(player);
        return true;
    }
}

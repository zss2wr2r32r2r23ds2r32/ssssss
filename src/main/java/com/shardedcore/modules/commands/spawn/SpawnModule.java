package com.shardedcore.modules.commands.spawn;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.ConfigUtil;
import com.shardedcore.util.GuiUtil;
import com.shardedcore.util.MessageUtil;
import com.shardedcore.util.TabCompleteHelper;
import com.shardedcore.util.TeleportHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class SpawnModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private TeleportHelper teleportHelper;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SpawnModule(ShardedCore plugin) {
        super(plugin, "spawn");
    }

    @Override
    public void enable() {
        teleportHelper = new TeleportHelper(plugin);
        teleportHelper.start();
        registerListener(this);
        registerCommand("spawn", this);
        registerCommand("setspawn", this);
        registerCommand("delspawn", this);
    }

    @Override
    public void disable() {
        if (teleportHelper != null) teleportHelper.shutdown();
        cooldowns.clear();
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("setspawn")) return handleSetSpawn(sender, args);
        if (cmd.equals("delspawn")) return handleDelSpawn(sender);
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("shardedcore.command.spawn")) {
            send(player, "no-permission");
            return true;
        }
        return handleSpawn(player);
    }

    private boolean handleSpawn(Player player) {
        Location spawn = readSpawn();
        if (spawn == null) {
            send(player, "not-set");
            return true;
        }
        if (isOnCooldown(player)) {
            send(player, "cooldown", "seconds", String.valueOf(cooldownRemaining(player)));
            return true;
        }
        int delay = config.getInt("teleport.delay-seconds", 5);
        String countdown = config.getString("teleport.countdown-actionbar",
                "&#16D223&lTELEPORT &7▷ &fTeleporting in &#16D223&n{seconds}s");
        String cancelled = config.getString("teleport.cancelled-actionbar",
                "&#16D223&lTELEPORT &7▷ &fYou moved &8— &7teleport cancelled.");
        teleportHelper.teleportDelayed(player, spawn, delay, countdown, p -> {
            applyCooldown(p);
            send(p, "teleported");
        }, () -> MessageUtil.sendActionBar(player, plugin, cancelled));
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("shardedcore.command.setspawn")) {
            send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        String name = args.length > 0 ? args[0] : "default";
        ConfigurationSection section = config.createSection("spawns." + name);
        GuiUtil.writeLocation(section, player.getLocation());
        ConfigUtil.saveYaml(config, new File(moduleFolder, "config.yml"));
        send(sender, "set", "name", name);
        return true;
    }

    private boolean handleDelSpawn(CommandSender sender) {
        if (!sender.hasPermission("shardedcore.command.delspawn")) {
            send(sender, "no-permission");
            return true;
        }
        config.set("spawns.default", null);
        ConfigUtil.saveYaml(config, new File(moduleFolder, "config.yml"));
        send(sender, "deleted");
        return true;
    }

    private Location readSpawn() {
        ConfigurationSection spawns = config.getConfigurationSection("spawns");
        if (spawns == null) return null;
        ConfigurationSection section = spawns.getConfigurationSection("default");
        if (section == null && !spawns.getKeys(false).isEmpty()) {
            section = spawns.getConfigurationSection(spawns.getKeys(false).iterator().next());
        }
        return GuiUtil.readLocation(section);
    }

    private boolean isOnCooldown(Player player) {
        int seconds = config.getInt("cooldown-seconds", 0);
        if (seconds <= 0 || player.hasPermission("shardedcore.spawn.bypass.cooldown")) return false;
        Long until = cooldowns.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    private int cooldownRemaining(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        if (until == null) return 0;
        return Math.max(0, (int) ((until - System.currentTimeMillis()) / 1000L));
    }

    private void applyCooldown(Player player) {
        int seconds = config.getInt("cooldown-seconds", 0);
        if (seconds <= 0 || player.hasPermission("shardedcore.spawn.bypass.cooldown")) return;
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!config.getBoolean("first-join-teleport", true)) return;
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            Location spawn = readSpawn();
            if (spawn != null) player.teleportAsync(spawn);
        }, config.getLong("first-join-delay-ticks", 5L));
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!config.getBoolean("override-bed-on-respawn", true)) return;
        if (config.getBoolean("respect-bed-spawn", false) && event.isBedSpawn()) return;
        Location spawn = readSpawn();
        if (spawn != null) event.setRespawnLocation(spawn);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (teleportHelper != null) teleportHelper.cancel(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("setspawn")) return List.of();
        if (!sender.hasPermission("shardedcore.command.setspawn")) return List.of();
        return TabCompleteHelper.filter(List.of("default"), args.length == 1 ? args[0] : "");
    }
}

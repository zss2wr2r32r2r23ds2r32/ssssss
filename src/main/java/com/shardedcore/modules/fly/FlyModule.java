package com.shardedcore.modules.fly;

import com.shardedcore.data.TimedPerks;
import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Configs;
import com.shardedcore.util.Tabs;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FlyModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private File regionFile;
    private FileConfiguration region;
    private final Map<UUID, Location> pos1 = new ConcurrentHashMap<>();
    private final Map<UUID, Location> pos2 = new ConcurrentHashMap<>();

    public FlyModule(ShardedCore plugin) {
        super(plugin, "fly");
    }

    @Override
    public void enable() {
        regionFile = new File(folder, "region.yml");
        region = Configs.load(regionFile);
        registerCommand("fly", this);
        registerListener(this);
        for (Player player : Bukkit.getOnlinePlayers()) enforce(player);
    }

    @Override
    public void disable() {
        cleanup();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            toggle(player);
            return true;
        }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "pos1" -> pos(player, true);
            case "pos2" -> pos(player, false);
            case "setregion" -> setRegion(player);
            default -> {
                send(player, "usage");
                yield true;
            }
        };
    }

    private boolean pos(Player player, boolean first) {
        if (!player.hasPermission("shardedcore.fly.admin")) {
            send(player, "no-permission");
            return true;
        }
        Location location = player.getLocation();
        (first ? pos1 : pos2).put(player.getUniqueId(), location);
        send(player, first ? "pos1" : "pos2",
                "x", String.valueOf(location.getBlockX()),
                "y", String.valueOf(location.getBlockY()),
                "z", String.valueOf(location.getBlockZ()));
        return true;
    }

    private boolean setRegion(Player player) {
        if (!player.hasPermission("shardedcore.fly.admin")) {
            send(player, "no-permission");
            return true;
        }
        Location a = pos1.get(player.getUniqueId());
        Location b = pos2.get(player.getUniqueId());
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
            send(player, "need-positions");
            return true;
        }
        if (!a.getWorld().equals(b.getWorld())) {
            send(player, "same-world");
            return true;
        }
        region = new YamlConfiguration();
        region.set("world", a.getWorld().getName());
        region.set("min-x", Math.min(a.getBlockX(), b.getBlockX()));
        region.set("min-y", Math.min(a.getBlockY(), b.getBlockY()));
        region.set("min-z", Math.min(a.getBlockZ(), b.getBlockZ()));
        region.set("max-x", Math.max(a.getBlockX(), b.getBlockX()));
        region.set("max-y", Math.max(a.getBlockY(), b.getBlockY()));
        region.set("max-z", Math.max(a.getBlockZ(), b.getBlockZ()));
        Configs.save(region, regionFile);
        send(player, "region-set");
        return true;
    }

    private void toggle(Player player) {
        if (player.getAllowFlight()) {
            player.setAllowFlight(false);
            player.setFlying(false);
            send(player, "off");
            return;
        }
        if (!allowed(player)) {
            send(player, "denied");
            return;
        }
        player.setAllowFlight(true);
        player.setFlying(true);
        send(player, "on");
    }

    private boolean allowed(Player player) {
        if (player.hasPermission("shardedcore.fly.bypass")) return true;
        if (TimedPerks.has(player.getUniqueId(), "fly")) return true;
        if (!inside(player.getLocation())) return false;
        return player.hasPermission("shardedcore.fly") || config.getBoolean("default-permission", true);
    }

    private boolean inside(Location location) {
        if (location.getWorld() == null) return false;
        String world = region.getString("world", cfg("world", "spawn"));
        if (!location.getWorld().getName().equalsIgnoreCase(world)) return false;
        if (!region.contains("min-x")) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= region.getInt("min-x") && x <= region.getInt("max-x")
                && y >= region.getInt("min-y") && y <= region.getInt("max-y")
                && z >= region.getInt("min-z") && z <= region.getInt("max-z");
    }

    private void enforce(Player player) {
        if (!player.getAllowFlight()) return;
        if (player.hasPermission("shardedcore.fly.bypass")) return;
        if (TimedPerks.has(player.getUniqueId(), "fly")) return;
        if (inside(player.getLocation())) return;
        player.setAllowFlight(false);
        player.setFlying(false);
        send(player, "left");
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        enforce(event.getPlayer());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        enforce(event.getPlayer());
    }

    @EventHandler
    public void onToggle(PlayerToggleFlightEvent event) {
        if (!event.isFlying()) return;
        if (allowed(event.getPlayer())) return;
        event.setCancelled(true);
        event.getPlayer().setAllowFlight(false);
        send(event.getPlayer(), "denied");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pos1.remove(event.getPlayer().getUniqueId());
        pos2.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("shardedcore.fly.admin")) {
            return Tabs.filter(List.of("pos1", "pos2", "setregion"), args[0]);
        }
        return List.of();
    }
}

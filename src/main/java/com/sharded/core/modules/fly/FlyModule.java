package com.sharded.core.modules.fly;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * /fly - region-restricted flight.
 *  - Works only inside the configured region in the configured world ("factions").
 *  - If no region is set, /fly refuses to work (set one with /fly pos1, /fly pos2, /fly setregion).
 *  - /fly speed <1-10> [player], /fly <player> to toggle others.
 *  - Players who leave the region or world lose flight, unless they are in
 *    creative/spectator or have sharded.fly.anywhere.
 */
public final class FlyModule extends Module implements CommandExecutor, TabCompleter {

    private final Set<UUID> flying = new HashSet<>();
    private final Set<UUID> noFallDamage = new HashSet<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public FlyModule(ShardedCore plugin) {
        super(plugin, "fly");
    }

    @Override
    protected void onEnable() {
        registerCommand("fly", this);
    }

    @Override
    protected void onDisable() {
        for (UUID uuid : Set.copyOf(flying)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) disableFlight(player, true);
        }
        flying.clear();
        noFallDamage.clear();
        pos1.clear();
        pos2.clear();
    }

    /* ----------------------------- region ----------------------------- */

    private boolean regionSet() {
        return config.getBoolean("region.set", false);
    }

    private String flyWorld() {
        return config.getString("world", "spawn");
    }

    private boolean inRegion(Location location) {
        if (!regionSet()) return false;
        if (location.getWorld() == null || !location.getWorld().getName().equalsIgnoreCase(flyWorld())) return false;
        double minX = Math.min(config.getDouble("region.x1"), config.getDouble("region.x2"));
        double maxX = Math.max(config.getDouble("region.x1"), config.getDouble("region.x2"));
        double minY = Math.min(config.getDouble("region.y1"), config.getDouble("region.y2"));
        double maxY = Math.max(config.getDouble("region.y1"), config.getDouble("region.y2"));
        double minZ = Math.min(config.getDouble("region.z1"), config.getDouble("region.z2"));
        double maxZ = Math.max(config.getDouble("region.z1"), config.getDouble("region.z2"));
        return location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    private boolean canFlyHere(Player player) {
        if (!regionSet()) return false;
        if (player.hasPermission("sharded.fly.anywhere")) return true;
        return inRegion(player.getLocation());
    }

    private void saveRegion(Location a, Location b) {
        config.set("region.set", true);
        config.set("region.x1", a.getX());
        config.set("region.y1", a.getY());
        config.set("region.z1", a.getZ());
        config.set("region.x2", b.getX());
        config.set("region.y2", b.getY());
        config.set("region.z2", b.getZ());
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save fly region: " + e.getMessage());
        }
    }

    /* ----------------------------- command ----------------------------- */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }

        if (args.length == 0) {
            toggleSelf(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "speed" -> {
                if (!player.hasPermission("sharded.fly.speed")) {
                    send(player, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    send(player, "speed-usage");
                    return true;
                }
                int speed;
                try {
                    speed = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    send(player, "speed-usage");
                    return true;
                }
                speed = Math.max(1, Math.min(10, speed));
                Player target = player;
                if (args.length >= 3) {
                    if (!player.hasPermission("sharded.fly.others")) {
                        send(player, "no-permission");
                        return true;
                    }
                    target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        send(player, "player-not-found", "%player%", args[2]);
                        return true;
                    }
                }
                target.setFlySpeed(speed / 10.0f);
                send(player, "speed-set", "%speed%", String.valueOf(speed), "%player%", target.getName());
                if (target != player) send(target, "speed-set-by-other", "%speed%", String.valueOf(speed));
            }
            case "pos1" -> {
                if (!player.hasPermission("sharded.fly.admin")) {
                    send(player, "no-permission");
                    return true;
                }
                pos1.put(player.getUniqueId(), player.getLocation());
                send(player, "pos-set", "%pos%", "1");
            }
            case "pos2" -> {
                if (!player.hasPermission("sharded.fly.admin")) {
                    send(player, "no-permission");
                    return true;
                }
                pos2.put(player.getUniqueId(), player.getLocation());
                send(player, "pos-set", "%pos%", "2");
            }
            case "setregion" -> {
                if (!player.hasPermission("sharded.fly.admin")) {
                    send(player, "no-permission");
                    return true;
                }
                Location a = pos1.get(player.getUniqueId());
                Location b = pos2.get(player.getUniqueId());
                if (a == null || b == null) {
                    send(player, "region-need-positions");
                    return true;
                }
                if (a.getWorld() == null || !a.getWorld().getName().equalsIgnoreCase(flyWorld())
                        || !b.getWorld().equals(a.getWorld())) {
                    send(player, "region-wrong-world", "%world%", flyWorld());
                    return true;
                }
                saveRegion(a, b);
                send(player, "region-saved", "%world%", flyWorld());
            }
            default -> {
                // /fly <player>
                if (!player.hasPermission("sharded.fly.others")) {
                    send(player, "no-permission");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    send(player, "player-not-found", "%player%", args[0]);
                    return true;
                }
                if (flying.contains(target.getUniqueId()) || target.getAllowFlight()) {
                    disableFlight(target, false);
                    send(player, "disabled-other", "%player%", target.getName());
                } else {
                    if (!regionSet()) {
                        send(player, "region-not-set");
                        return true;
                    }
                    enableFlight(target);
                    send(player, "enabled-other", "%player%", target.getName());
                }
            }
        }
        return true;
    }

    private void toggleSelf(Player player) {
        if (!player.hasPermission("sharded.fly.use")) {
            send(player, "no-permission");
            return;
        }
        if (flying.contains(player.getUniqueId())) {
            disableFlight(player, false);
            send(player, "disabled");
            return;
        }
        if (!regionSet()) {
            send(player, "region-not-set");
            return;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(flyWorld()) && !player.hasPermission("sharded.fly.anywhere")) {
            send(player, "wrong-world", "%world%", flyWorld());
            return;
        }
        if (!canFlyHere(player)) {
            send(player, "not-in-region");
            return;
        }
        enableFlight(player);
        send(player, "enabled");
    }

    private void enableFlight(Player player) {
        flying.add(player.getUniqueId());
        player.setAllowFlight(true);
        player.setFlying(true);
    }

    private void disableFlight(Player player, boolean silent) {
        flying.remove(player.getUniqueId());
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.isFlying() || player.getAllowFlight()) {
            noFallDamage.add(player.getUniqueId());
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    /* ----------------------------- enforcement ----------------------------- */

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        Player player = event.getPlayer();
        if (!flying.contains(player.getUniqueId())) return;
        // Creative/spectator players are never cancelled, per config.
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.hasPermission("sharded.fly.anywhere")) return;

        Location to = event.getTo();
        boolean allowed = to.getWorld() != null
                && to.getWorld().getName().equalsIgnoreCase(flyWorld())
                && inRegion(to);
        if (!allowed) {
            disableFlight(player, false);
            send(player, "left-region");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (noFallDamage.remove(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flying.remove(event.getPlayer().getUniqueId());
        noFallDamage.remove(event.getPlayer().getUniqueId());
        pos1.remove(event.getPlayer().getUniqueId());
        pos2.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("speed"));
            if (sender.hasPermission("sharded.fly.admin")) {
                options.addAll(List.of("pos1", "pos2", "setregion"));
            }
            if (sender.hasPermission("sharded.fly.others")) {
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            }
            options.removeIf(o -> !o.toLowerCase().startsWith(args[0].toLowerCase()));
            return options;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("speed")) {
            return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        }
        return List.of();
    }
}

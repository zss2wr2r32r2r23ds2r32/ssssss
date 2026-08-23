package com.sharded.core.modules.outpost;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.GameEventCoordinator;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Outpost capture — solo capture awards tokens at 100%. */
public final class OutpostModule extends Module implements CommandExecutor, TabCompleter {

    private final RegionSetup setup = new RegionSetup();
    private CuboidRegion region;
    private GameEventCoordinator coordinator;
    private boolean active;
    private double capturePercent;
    private UUID capturingPlayer;
    private final Set<UUID> inside = new HashSet<>();
    private int tickTask = -1;

    public OutpostModule(ShardedCore plugin) {
        super(plugin, "outpost");
    }

    @Override
    protected void onEnable() {
        coordinator = GameEventCoordinator.get() != null
                ? GameEventCoordinator.get() : new GameEventCoordinator(plugin);
        reloadRegion();
        registerCommand("outpost", this);
        tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    @Override
    protected void onDisable() {
        if (tickTask >= 0) Bukkit.getScheduler().cancelTask(tickTask);
        active = false;
    }

    private void reloadRegion() {
        region = CuboidRegion.fromSection(config.getConfigurationSection("region"));
    }

    public long millisUntilStart() {
        return coordinator == null ? 0 : coordinator.millisUntilOutpost();
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            send(player, "info", "%time%", TimeFormat.hms(millisUntilStart()));
            return true;
        }
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (!player.hasPermission("sharded.outpost.admin")) {
            send(sender, "no-permission");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("pos1")) {
            setup.setPos1(player, player.getLocation());
            send(player, "pos1-set");
            return true;
        }
        if (sub.equals("pos2")) {
            setup.setPos2(player, player.getLocation());
            send(player, "pos2-set");
            return true;
        }
        if (sub.equals("setregion")) {
            if (!isSpawnWorld(player.getWorld().getName())) {
                send(player, "spawn-world-only");
                return true;
            }
            CuboidRegion built = setup.build(player);
            if (built == null) {
                send(player, "need-positions");
                return true;
            }
            region = built;
            built.write(config.createSection("region"));
            saveConfig();
            send(player, "region-set");
            return true;
        }
        send(player, "usage");
        return true;
    }

    private boolean isSpawnWorld(String world) {
        List<String> allowed = config.getStringList("allowed-worlds");
        if (allowed.isEmpty()) allowed = List.of("spawn");
        for (String w : allowed) {
            if (w.equalsIgnoreCase(world)) return true;
        }
        return false;
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[outpost] Could not save config: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (region == null || !active) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        Player player = event.getPlayer();
        if (region.contains(event.getTo())) inside.add(player.getUniqueId());
        else inside.remove(player.getUniqueId());
    }

    private void tick() {
        if (region == null) return;
        if (!active) {
            if (coordinator.canStartOutpost()) startEvent();
            return;
        }
        refreshInside();
        List<UUID> players = new ArrayList<>(inside);
        if (players.size() == 1) {
            UUID uuid = players.getFirst();
            capturingPlayer = uuid;
            double rate = config.getDouble("capture-percent-per-second", 1.0);
            capturePercent = Math.min(100.0, capturePercent + rate);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                String bar = TimeFormat.replacePlaceholders(
                        config.getString("actionbar", "&7Outpost: &f%percent%%"),
                        0).replace("%percent%", String.format(Locale.US, "%.0f", capturePercent));
                p.sendActionBar(Text.c(bar));
            }
            if (capturePercent >= 100.0) {
                completeCapture(uuid);
            }
        } else {
            capturingPlayer = null;
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                p.sendActionBar(Text.c(config.getString("actionbar-contested",
                        "&cOutpost contested — solo capture required!")));
            }
        }
    }

    private void refreshInside() {
        inside.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (region.contains(player)) inside.add(player.getUniqueId());
        }
    }

    private void startEvent() {
        active = true;
        capturePercent = 0;
        capturingPlayer = null;
        coordinator.setOutpostActive(true);
        Bukkit.broadcast(Text.c(raw("broadcast-start")));
    }

    private void completeCapture(UUID uuid) {
        long reward = config.getLong("token-reward", 500);
        TokenService tokens = plugin.modules().tokens();
        if (tokens != null) tokens.give(uuid, reward);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            send(player, "captured", "%amount%", String.valueOf(reward));
        }
        endEvent();
    }

    private void endEvent() {
        active = false;
        capturePercent = 0;
        capturingPlayer = null;
        inside.clear();
        coordinator.setOutpostActive(false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.outpost.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion");
        return List.of();
    }
}

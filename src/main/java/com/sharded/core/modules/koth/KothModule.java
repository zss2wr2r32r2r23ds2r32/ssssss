package com.sharded.core.modules.koth;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.GameEventCoordinator;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** King of the Hill — points for hits and standing in region; top 3 rewarded. */
public final class KothModule extends Module implements CommandExecutor, TabCompleter {

    private final RegionSetup setup = new RegionSetup();
    private CuboidRegion region;
    private GameEventCoordinator coordinator;
    private boolean active;
    private long eventEndsAt;
    private final Map<UUID, Double> points = new HashMap<>();
    private final Set<UUID> inside = new HashSet<>();
    private int tickTask = -1;

    public KothModule(ShardedCore plugin) {
        super(plugin, "koth");
    }

    @Override
    protected void onEnable() {
        if (GameEventCoordinator.get() == null) coordinator = new GameEventCoordinator(plugin);
        else coordinator = GameEventCoordinator.get();
        reloadRegion();
        registerCommand("koth", this);
        tickTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    @Override
    protected void onDisable() {
        if (tickTask >= 0) Bukkit.getScheduler().cancelTask(tickTask);
        active = false;
        points.clear();
    }

    private void reloadRegion() {
        region = CuboidRegion.fromSection(config.getConfigurationSection("region"));
    }

    public long millisUntilStart() {
        return coordinator == null ? 0 : coordinator.millisUntilKoth();
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
        if (!player.hasPermission("sharded.koth.admin")) {
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!active || region == null) return;
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj
                && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null) return;
        if (!region.contains(victim.getLocation()) && !region.contains(attacker.getLocation())) return;
        addPoints(attacker.getUniqueId(), config.getDouble("points-per-hit", 5));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!active || region == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (region.contains(event.getTo())) inside.add(event.getPlayer().getUniqueId());
        else inside.remove(event.getPlayer().getUniqueId());
    }

    private void tick() {
        if (region == null) return;
        if (!active) {
            if (coordinator.canStartKoth()) startEvent();
            return;
        }
        if (System.currentTimeMillis() >= eventEndsAt) {
            finishEvent();
            return;
        }
        double standRate = config.getDouble("points-per-second-standing", 1.0);
        for (UUID uuid : inside) {
            addPoints(uuid, standRate);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                String bar = config.getString("actionbar", "&dKOTH &7| &f%points% pts &7| &f%time%")
                        .replace("%points%", String.format(Locale.US, "%.0f", points.getOrDefault(uuid, 0.0)))
                        .replace("%time%", TimeFormat.hms(eventEndsAt - System.currentTimeMillis()));
                p.sendActionBar(Text.c(bar));
            }
        }
    }

    private void addPoints(UUID uuid, double amount) {
        points.merge(uuid, amount, Double::sum);
    }

    private void startEvent() {
        active = true;
        points.clear();
        inside.clear();
        eventEndsAt = System.currentTimeMillis() + config.getLong("duration-seconds", 300) * 1000L;
        coordinator.setKothActive(true);
        Bukkit.broadcast(Text.c(raw("broadcast-start")));
    }

    private void finishEvent() {
        active = false;
        List<Map.Entry<UUID, Double>> top = points.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(3)
                .toList();
        TokenService tokens = plugin.modules().tokens();
        for (int i = 0; i < top.size(); i++) {
            long reward = config.getLong("rewards.rank-" + (i + 1), 1000L - i * 200L);
            UUID uuid = top.get(i).getKey();
            if (tokens != null) tokens.give(uuid, reward);
            Bukkit.broadcast(Text.c(raw("reward-line",
                    "%rank%", String.valueOf(i + 1),
                    "%player%", OfflinePlayers.name(uuid),
                    "%amount%", String.valueOf(reward))));
        }
        points.clear();
        inside.clear();
        coordinator.setKothActive(false);
    }

    private void saveConfig() {
        try {
            config.save(new File(moduleFolder(), "config.yml"));
        } catch (Exception e) {
            plugin.getLogger().warning("[koth] Could not save config: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.koth.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion");
        return List.of();
    }
}

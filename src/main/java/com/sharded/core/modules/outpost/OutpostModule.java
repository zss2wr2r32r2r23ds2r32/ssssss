package com.sharded.core.modules.outpost;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.tokens.TokenService;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.EventRewards;
import com.sharded.core.util.EventSounds;
import com.sharded.core.util.GameEventCoordinator;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.RegionSetup;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import com.sharded.core.util.TimeFormat;
import org.bukkit.Bukkit;
import org.bukkit.MusicInstrument;
import org.bukkit.boss.BarColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
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
    private long eventStartedAt;
    private long lastSoundAt;
    private boolean contested;

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
        if (coordinator != null) coordinator.bossBar().hide("outpost");
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

    public boolean isInside(Player player) {
        return region != null && region.contains(player);
    }

    public double capturePercent() {
        return capturePercent;
    }

    public String capturerName() {
        if (contested) return "Contested";
        if (capturingPlayer == null) return "N/A";
        return OfflinePlayers.name(capturingPlayer);
    }

    public long emptyTimeRemainingMs() {
        long maxUnclaimedMs = config.getLong("max-unclaimed-seconds", 600) * 1000L;
        return Math.max(0, maxUnclaimedMs - (System.currentTimeMillis() - eventStartedAt));
    }

    public String modulePrefix() {
        return config.getString("prefix", "&#FF2727&lOUTPOST &8▷ &r");
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
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("start")) {
            if (!sender.hasPermission("sharded.outpost.admin")) {
                send(sender, "no-permission");
                return true;
            }
            if (active) {
                send(sender, "already-active");
                return true;
            }
            if (region == null) {
                send(sender, "no-region");
                return true;
            }
            startEvent();
            send(sender, "started");
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (region == null) return;
        if (!region.contains(event.getBlock().getLocation())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "no-place");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (active && coordinator != null) coordinator.bossBar().syncPlayers();
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
            if (coordinator != null && coordinator.canStartOutpost()) startEvent();
            return;
        }
        refreshInside();
        List<UUID> players = new ArrayList<>(inside);
        contested = players.size() > 1;
        if (players.size() == 1) {
            UUID uuid = players.getFirst();
            capturingPlayer = uuid;
            double rate = config.getDouble("capture-percent-per-second", 1.0);
            capturePercent = Math.min(100.0, capturePercent + rate);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                String bar = config.getString("actionbar", "&7Outpost: &f%percent%%")
                        .replace("%percent%", String.format(Locale.US, "%.0f", capturePercent));
                p.sendActionBar(Text.c(modulePrefix() + bar));
            }
            if (capturePercent >= 100.0) {
                completeCapture(uuid);
                return;
            }
        } else {
            capturingPlayer = null;
            if (contested) {
                for (UUID uuid : players) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    p.sendActionBar(Text.c(modulePrefix() + config.getString("actionbar-contested",
                            "&cContested — solo capture required!")));
                }
            }
        }
        updateBossBar();
        long maxUnclaimedMs = config.getLong("max-unclaimed-seconds", 600) * 1000L;
        if (System.currentTimeMillis() - eventStartedAt >= maxUnclaimedMs && capturePercent < 100.0) {
            Bukkit.broadcast(Text.c(modulePrefix() + raw("timeout")));
            endEvent();
        }
    }

    private void updateBossBar() {
        if (coordinator == null) return;
        long maxUnclaimedMs = config.getLong("max-unclaimed-seconds", 600) * 1000L;
        long emptyRemaining = emptyTimeRemainingMs();
        String capturer = capturerName();
        String title;
        double progress;
        if (inside.isEmpty()) {
            title = config.getString("bossbar-empty",
                            "%prefix%&fN/A &8| &fEnds in &f%empty_time%")
                    .replace("%prefix%", modulePrefix())
                    .replace("%empty_time%", TimeFormat.hms(emptyRemaining));
            progress = maxUnclaimedMs <= 0 ? 0.0 : (double) emptyRemaining / maxUnclaimedMs;
        } else {
            title = config.getString("bossbar-active",
                            "%prefix%&f%capturer% &8| &f%percent%%")
                    .replace("%prefix%", modulePrefix())
                    .replace("%capturer%", capturer)
                    .replace("%percent%", String.format(Locale.US, "%.0f", capturePercent));
            progress = capturePercent / 100.0;
        }
        coordinator.bossBar().show("outpost", title, BarColor.RED, progress);
        coordinator.bossBar().syncPlayers();
    }

    private void maybePlayActiveSound() {
        long interval = config.getLong("active-sound-interval-seconds", 90) * 1000L;
        long now = System.currentTimeMillis();
        if (interval > 0 && now - lastSoundAt < interval) return;
        lastSoundAt = now;
        MusicInstrument instrument = EventSounds.parseInstrument(
                config.getString("active-sound-instrument", "PONDER_GOAT_HORN"),
                MusicInstrument.PONDER_GOAT_HORN);
        EventSounds.playInstrumentToWorlds(instrument, config.getStringList("allowed-worlds"));
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
        contested = false;
        eventStartedAt = System.currentTimeMillis();
        lastSoundAt = 0;
        coordinator.setOutpostActive(true);
        Bukkit.broadcast(Text.c(modulePrefix() + raw("broadcast-start")));
        maybePlayActiveSound();
    }

    private void completeCapture(UUID uuid) {
        ConfigurationSection rewards = config.getConfigurationSection("capture-rewards");
        long reward = rewards != null ? rewards.getLong("tokens", config.getLong("token-reward", 500)) : config.getLong("token-reward", 500);
        if (rewards != null) {
            EventRewards.grant(plugin, uuid, rewards);
        } else {
            TokenService tokens = plugin.modules().tokens();
            if (tokens != null) tokens.give(uuid, reward);
        }
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
        contested = false;
        inside.clear();
        if (coordinator != null) coordinator.bossBar().hide("outpost");
        coordinator.setOutpostActive(false);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.outpost.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "pos1", "pos2", "setregion", "start");
        return List.of();
    }
}

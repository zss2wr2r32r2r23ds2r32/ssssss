package com.sharded.core.modules.killstreaks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.MessageUtil;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class KillstreaksModule extends Module implements CommandExecutor, TabCompleter {

    private KillstreakDatabase database;
    private final Map<UUID, Map<UUID, List<Long>>> recentKills = new ConcurrentHashMap<>();

    public KillstreaksModule(ShardedCore plugin) {
        super(plugin, "killstreaks");
    }

    @Override
    protected void onEnable() {
        try {
            database = new KillstreakDatabase(plugin, moduleFolder());
        } catch (Exception e) {
            throw new IllegalStateException("Could not open killstreak database", e);
        }
        registerCommand("killstreak", this);
    }

    @Override
    protected void onDisable() {
        if (database != null) database.close();
        database = null;
    }

    public KillstreakDatabase database() {
        return database;
    }

    public int streak(UUID uuid) {
        return database == null ? 0 : database.getCurrent(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.killstreak.use")) {
            send(sender, "no-permission");
            return true;
        }

        UUID targetId;
        String targetName;
        boolean best;

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
            best = false;
        } else if (args.length == 1 && args[0].equalsIgnoreCase("best")) {
            if (!(sender instanceof Player player)) {
                send(sender, "players-only");
                return true;
            }
            targetId = player.getUniqueId();
            targetName = player.getName();
            best = true;
        } else if (args.length == 1) {
            if (!sender.hasPermission("sharded.killstreak.others")) {
                send(sender, "no-permission-others");
                return true;
            }
            OfflinePlayer target = OfflinePlayers.resolve(args[0]);
            targetId = target.getUniqueId();
            targetName = target.getName() == null ? args[0] : target.getName();
            best = false;
        } else if (args.length == 2 && args[1].equalsIgnoreCase("best")) {
            if (!sender.hasPermission("sharded.killstreak.others")) {
                send(sender, "no-permission-others");
                return true;
            }
            OfflinePlayer target = OfflinePlayers.resolve(args[0]);
            targetId = target.getUniqueId();
            targetName = target.getName() == null ? args[0] : target.getName();
            best = true;
        } else {
            send(sender, "usage");
            return true;
        }

        if (database == null) return true;
        int value = best ? database.getBest(targetId) : database.getCurrent(targetId);
        if (sender instanceof Player self && self.getUniqueId().equals(targetId)) {
            send(sender, best ? "self-best" : "self-current", "%streak%", String.valueOf(value));
        } else {
            send(sender, best ? "other-best" : "other-current",
                    "%player%", targetName, "%streak%", String.valueOf(value));
        }
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.killstreak.use")) return java.util.List.of();
        if (args.length == 1) {
            java.util.List<String> options = new java.util.ArrayList<>();
            options.add("best");
            if (sender.hasPermission("sharded.killstreak.others")) {
                options.addAll(TabCompleteHelper.onlinePlayers(""));
            }
            return TabCompleteHelper.filter(args[0], options);
        }
        if (args.length == 2 && sender.hasPermission("sharded.killstreak.others")) {
            return TabCompleteHelper.filter(args[1], "best");
        }
        return java.util.List.of();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (database != null) database.setStreak(victim.getUniqueId(), 0);

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || database == null) return;
        if (!shouldCountKill(killer, victim)) return;

        int streak = database.getCurrent(killer.getUniqueId()) + 1;
        database.setStreak(killer.getUniqueId(), streak);

        ConfigurationSection rewards = config.getConfigurationSection("rewards." + streak);
        if (rewards == null) return;

        String broadcast = rewards.getString("broadcast", "");
        if (!broadcast.isEmpty()) {
            announce(Text.apply(broadcast, "%player%", killer.getName(), "%streak%", String.valueOf(streak)), killer);
        }

        send(killer, "milestone", "%streak%", String.valueOf(streak));

        for (String cmd : rewards.getStringList("commands")) {
            cmd = cmd.replace("%player%", killer.getName())
                    .replace("%streak%", String.valueOf(streak))
                    .replace("%uuid%", killer.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    private boolean shouldCountKill(Player killer, Player victim) {
        if (!config.getBoolean("anti-farm.enabled", true)) return true;
        if (killer.hasPermission("sharded.killstreak.farm.bypass")) return true;

        if (config.getBoolean("anti-farm.block-same-ip", true)) {
            String killerIp = killer.getAddress() == null ? null : killer.getAddress().getAddress().getHostAddress();
            String victimIp = victim.getAddress() == null ? null : victim.getAddress().getAddress().getHostAddress();
            if (killerIp != null && killerIp.equals(victimIp)) {
                send(killer, "farm-same-ip");
                return false;
            }
        }

        long windowMs = config.getLong("anti-farm.window-minutes", 60L) * 60_000L;
        int limit = config.getInt("anti-farm.same-victim-limit", 3);
        long now = System.currentTimeMillis();

        Map<UUID, List<Long>> byVictim = recentKills.computeIfAbsent(killer.getUniqueId(), k -> new ConcurrentHashMap<>());
        List<Long> times = byVictim.computeIfAbsent(victim.getUniqueId(), k -> new ArrayList<>());
        times.add(now);
        Iterator<Long> it = times.iterator();
        while (it.hasNext()) {
            if (now - it.next() > windowMs) it.remove();
        }
        if (times.size() > limit) {
            send(killer, "farm-same-victim", "%player%", victim.getName());
            return false;
        }
        return true;
    }

    private void announce(String message, Player killer) {
        MessageUtil.Delivery mode = resolveDelivery("broadcast");
        var component = Text.c(message);
        switch (mode) {
            case ACTIONBAR -> {
                killer.sendActionBar(component);
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online != killer) online.sendMessage(component);
                }
            }
            case BOTH -> {
                Bukkit.getServer().broadcast(component);
                killer.sendActionBar(component);
            }
            default -> Bukkit.getServer().broadcast(component);
        }
    }
}

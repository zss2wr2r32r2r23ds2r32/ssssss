package com.sharded.core.modules.killstreaks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
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

import java.util.UUID;

public final class KillstreaksModule extends Module implements CommandExecutor, TabCompleter {

    private KillstreakDatabase database;

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

    private void announce(String message, Player killer) {
        if (config.getBoolean("announce-actionbar", false)) {
            killer.sendActionBar(Text.c(message));
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online != killer) online.sendMessage(Text.c(message));
            }
        } else {
            Bukkit.getServer().broadcast(Text.c(message));
        }
    }
}

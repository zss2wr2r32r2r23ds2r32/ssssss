package com.shardedcore.modules.welcome;

import com.shardedcore.ShardedCore;
import com.shardedcore.module.Module;
import com.shardedcore.util.Sounds;
import com.shardedcore.util.Tabs;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WelcomeModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private final Map<UUID, Long> window = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> welcomed = new ConcurrentHashMap<>();

    public WelcomeModule(ShardedCore plugin) {
        super(plugin, "welcome");
    }

    @Override
    public void enable() {
        registerCommand("w", this);
        registerListener(this);
    }

    @Override
    public void disable() {
        window.clear();
        welcomed.clear();
        cleanup();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        window.put(joined.getUniqueId(), System.currentTimeMillis());
        welcomed.put(joined.getUniqueId(), ConcurrentHashMap.newKeySet());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!joined.isOnline()) return;
            String line = cfg("message", "").replace("%player%", joined.getName());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.equals(joined)) sendRaw(player, line);
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        window.remove(uuid);
        welcomed.remove(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            send(player, "usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !window.containsKey(target.getUniqueId())) {
            send(player, "unknown", "player", args[0]);
            sound(player, "sounds.error");
            return true;
        }
        if (target.equals(player)) {
            send(player, "self");
            sound(player, "sounds.error");
            return true;
        }
        long life = config.getLong("window", 120) * 1000L;
        if (System.currentTimeMillis() - window.getOrDefault(target.getUniqueId(), 0L) > life) {
            send(player, "unknown", "player", target.getName());
            return true;
        }
        Set<UUID> already = welcomed.computeIfAbsent(target.getUniqueId(), ignored -> ConcurrentHashMap.newKeySet());
        if (!already.add(player.getUniqueId())) {
            send(player, "already", "player", target.getName());
            sound(player, "sounds.error");
            return true;
        }
        int max = config.getInt("max-rewards", 0);
        if (max > 0 && already.size() > max) {
            already.remove(player.getUniqueId());
            send(player, "full", "player", target.getName());
            return true;
        }
        String chat = cfg("chat-line", "welcome %player%").replace("%player%", target.getName());
        player.chat(chat);
        int amount = config.getInt("reward.amount", 1);
        for (String line : config.getStringList("reward.commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line
                    .replace("%player%", player.getName())
                    .replace("%new%", target.getName())
                    .replace("%amount%", String.valueOf(amount)));
        }
        send(player, "rewarded", "player", target.getName(), "amount", String.valueOf(amount));
        Sounds.play(player, config.getConfigurationSection("sound"));
        Sounds.play(player, config.getConfigurationSection("sounds.reward"));
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Tabs.players(args[0]) : java.util.List.of();
    }
}

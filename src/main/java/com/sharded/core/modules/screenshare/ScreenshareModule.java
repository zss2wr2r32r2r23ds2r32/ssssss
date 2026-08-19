package com.sharded.core.modules.screenshare;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.punishments.PunishmentsModule;
import com.sharded.core.util.DurationUtil;
import com.sharded.core.util.OfflinePlayers;
import com.sharded.core.util.TabCompleteHelper;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Screenshare countdown — logout during SS bans for SS-Refuse. */
public final class ScreenshareModule extends Module implements CommandExecutor, TabCompleter {

    private record Session(UUID staffId, long endsAt, BukkitTask task) {
    }

    private final Map<UUID, Session> active = new ConcurrentHashMap<>();

    public ScreenshareModule(ShardedCore plugin) {
        super(plugin, "screenshare");
    }

    @Override
    protected void onEnable() {
        registerListener(this);
        registerCommand("screenshare", this);
    }

    @Override
    protected void onDisable() {
        for (Session session : active.values()) {
            if (session.task() != null) session.task().cancel();
        }
        active.clear();
    }

    public boolean isFrozen(UUID uuid) {
        return active.containsKey(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sharded.screenshare.use")) {
            send(sender, "no-permission");
            return true;
        }
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            send(sender, "not-online", "%player%", args[0]);
            return true;
        }
        if (active.containsKey(target.getUniqueId())) {
            stop(target, false);
            send(sender, "stopped", "%player%", target.getName());
            return true;
        }
        if (!(sender instanceof Player staff)) {
            send(sender, "players-only");
            return true;
        }
        start(staff, target);
        send(sender, "started", "%player%", target.getName());
        return true;
    }

    private void start(Player staff, Player target) {
        stop(target, false);
        long seconds = config.getLong("countdown-seconds", 120L);
        long endsAt = System.currentTimeMillis() + seconds * 1000L;
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(target), 20L, 20L);
        active.put(target.getUniqueId(), new Session(staff.getUniqueId(), endsAt, task));
        tick(target);
    }

    private void stop(Player target, boolean banOnQuit) {
        Session session = active.remove(target.getUniqueId());
        if (session != null && session.task() != null) session.task().cancel();
        if (banOnQuit) applyRefuseBan(target);
    }

    private void tick(Player target) {
        Session session = active.get(target.getUniqueId());
        if (session == null) return;
        long remaining = Math.max(0, (session.endsAt() - System.currentTimeMillis()) / 1000L);
        if (remaining <= 0) {
            stop(target, false);
            return;
        }
        if (config.getBoolean("action-bar.enabled", true)) {
            target.sendActionBar(Text.c(buildActionBar((int) remaining)));
        }
        if (remaining % config.getLong("chat-warning-interval-seconds", 5L) == 0) {
            for (String line : messages.getStringList("messages.chat-warning-lines")) {
                target.sendMessage(Text.c(Text.apply(line, "%seconds%", String.valueOf(remaining))));
            }
        }
        target.showTitle(net.kyori.adventure.title.Title.title(
                Text.c(messages.getString("messages.title", "&#FF0000&lSCREENSHARE")),
                Text.c(Text.apply(messages.getString("messages.subtitle", "&f%seconds%s left"), "%seconds%", String.valueOf(remaining))),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(0),
                        java.time.Duration.ofMillis(1100),
                        java.time.Duration.ofMillis(0))));
    }

    private String buildActionBar(int seconds) {
        int length = config.getInt("action-bar.bar-length", 20);
        int filled = (int) Math.round((double) seconds / config.getLong("countdown-seconds", 120L) * length);
        String fill = config.getString("action-bar.filled-color", "&#FF007B") + config.getString("action-bar.bar-char", "|").repeat(Math.max(0, filled));
        String empty = config.getString("action-bar.empty-color", "&#FF0000") + config.getString("action-bar.bar-char", "|").repeat(Math.max(0, length - filled));
        return config.getString("action-bar.format", "%bar% &f%seconds%s")
                .replace("%bar%", fill + empty)
                .replace("%seconds%", String.valueOf(seconds));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (!active.containsKey(event.getPlayer().getUniqueId())) return;
        stop(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!active.containsKey(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        send(event.getPlayer(), "command-blocked");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (!active.containsKey(event.getPlayer().getUniqueId())) return;
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            event.setTo(event.getFrom());
        }
    }

    private void applyRefuseBan(Player target) {
        PunishmentsModule punishments = plugin.modules().get(PunishmentsModule.class);
        String reason = config.getString("ban.reason", "SS-Refuse");
        String duration = config.getString("ban.duration", "7d");
        if (punishments != null) {
            punishments.ban(Bukkit.getConsoleSender(), target, reason, duration);
            return;
        }
        String cmd = config.getString("ban.fallback-command", "ban %player% SS-Refuse")
                .replace("%player%", target.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.startsWith("/") ? cmd.substring(1) : cmd);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return TabCompleteHelper.onlinePlayers(args[0]);
        return List.of();
    }
}

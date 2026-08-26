package com.sharded.core.modules.arena;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.modules.protect.ProtectModule;
import com.sharded.core.util.CuboidRegion;
import com.sharded.core.util.TabCompleteHelper;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Arena snapshots and resets for side PvP regions. */
public final class ArenaModule extends Module implements CommandExecutor, TabCompleter {

    private static final List<String> SIDE_ARENAS = List.of("side1", "side2", "side3", "side4");

    private ArenaService service;
    private BukkitTask autoResetTask;

    public ArenaModule(ShardedCore plugin) {
        super(plugin, "arena");
    }

    @Override
    protected void onEnable() {
        service = new ArenaService(plugin, moduleFolder());
        registerCommand("arena", this);
        startAutoReset();
    }

    @Override
    protected void onDisable() {
        if (autoResetTask != null) autoResetTask.cancel();
    }

    private void startAutoReset() {
        if (!config.getBoolean("auto-reset.enabled", true)) return;
        long minutes = config.getLong("auto-reset.interval-minutes", 15L);
        long ticks = Math.max(1200L, minutes * 60L * 20L);
        autoResetTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!config.getBoolean("auto-reset.enabled", true)) return;
            resetSideArenas(true, null);
        }, ticks, ticks);
    }

    private CuboidRegion regionFor(String arenaId) {
        ProtectModule protect = plugin.modules().get(ProtectModule.class);
        if (protect == null) return null;
        return protect.region(arenaId);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            send(sender, "usage");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("snapshot") && args.length >= 2) {
            if (!sender.hasPermission("sharded.arena.admin")) {
                send(sender, "no-permission");
                return true;
            }
            List<String> ids = resolveArenaIds(args[1]);
            int total = 0;
            for (String id : ids) {
                CuboidRegion region = regionFor(id);
                if (region == null) {
                    send(sender, "no-region", "%arena%", id);
                    continue;
                }
                total += service.snapshot(id, region);
            }
            send(sender, "snapshot-done", "%count%", String.valueOf(total));
            return true;
        }
        if (sub.equals("reset") && args.length >= 2) {
            if (!sender.hasPermission("sharded.arena.admin")) {
                send(sender, "no-permission");
                return true;
            }
            boolean fast = args.length >= 3 && args[2].equalsIgnoreCase("fast");
            List<String> ids = resolveArenaIds(args[1]);
            if (ids.equals(SIDE_ARENAS)) {
                resetSideArenas(fast, sender);
            } else {
                for (String id : ids) {
                    if (!service.hasSnapshot(id)) {
                        send(sender, "no-snapshot", "%arena%", id);
                        continue;
                    }
                    service.reset(id, fast, () -> send(sender, "reset-done", "%arena%", id));
                }
            }
            return true;
        }
        send(sender, "usage");
        return true;
    }

    private void resetSideArenas(boolean fast, CommandSender notify) {
        List<String> pending = new ArrayList<>();
        for (String id : SIDE_ARENAS) {
            if (service.hasSnapshot(id)) pending.add(id);
        }
        if (pending.isEmpty()) {
            if (notify != null) send(notify, "no-snapshots");
            return;
        }
        service.resetAll(pending, fast, id -> {
            if (notify != null) send(notify, "reset-done", "%arena%", id);
        });
    }

    private List<String> resolveArenaIds(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.equals("side1-side4") || lower.equals("sides") || lower.equals("all")) {
            return SIDE_ARENAS;
        }
        return List.of(lower);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("sharded.arena.admin")) return List.of();
        if (args.length == 1) return TabCompleteHelper.filter(args[0], "snapshot", "reset");
        if (args.length == 2) {
            List<String> ids = new ArrayList<>(SIDE_ARENAS);
            ids.add("side1-side4");
            return TabCompleteHelper.filter(args[1], ids);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("reset")) {
            return TabCompleteHelper.filter(args[2], "fast");
        }
        return List.of();
    }
}

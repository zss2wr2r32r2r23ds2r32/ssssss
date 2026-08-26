package com.shardedcore.modules.rtp;

import com.shardedcore.util.SafeLocationFinder;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class RtpSafeSpotPool {

    private final RtpModule module;
    private final Map<String, Deque<Location>> pools = new ConcurrentHashMap<>();
    private BukkitTask task;

    RtpSafeSpotPool(RtpModule module) { this.module = module; }

    void start() {
        int size = module.rtpConfig().getInt("safe-pool-size", 10);
        ConfigurationSection dests = module.rtpConfig().getConfigurationSection("destinations");
        if (dests != null) for (String key : dests.getKeys(false)) pools.put(key, new ArrayDeque<>());
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(module.plugin(), () -> {
            for (var e : pools.entrySet()) {
                if (e.getValue().size() >= size) continue;
                ConfigurationSection dest = module.destination(e.getKey());
                if (dest == null) continue;
                World world = Bukkit.getWorld(dest.getString("world", "world"));
                if (world == null) continue;
                Location loc = SafeLocationFinder.find(world, module.rtpConfig());
                if (loc != null) Bukkit.getScheduler().runTask(module.plugin(), () -> e.getValue().offerLast(loc));
            }
        }, 20L, module.rtpConfig().getInt("safe-pool-refill-ticks", 40));
    }

    void shutdown() { if (task != null) task.cancel(); pools.clear(); }

    Location poll(String id, World world) {
        Deque<Location> pool = pools.get(id);
        if (pool == null) return null;
        Location loc = pool.pollFirst();
        return loc != null && loc.getWorld() != null && loc.getWorld().equals(world) ? loc : null;
    }
}

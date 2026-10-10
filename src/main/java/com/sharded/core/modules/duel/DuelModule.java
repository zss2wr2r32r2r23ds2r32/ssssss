package com.sharded.core.modules.duel;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import org.bukkit.Bukkit;

/** Ensures ShardedCore links to Realized Duels instead of overriding /duel. */
public final class DuelModule extends Module {

    public DuelModule(ShardedCore plugin) {
        super(plugin, "duel");
    }

    @Override
    protected void onEnable() {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.duelsHook().isAvailable()) {
                plugin.duelsHook().refreshCommands();
                plugin.getLogger().info("[duel] Linked /duel and /queue to Realized Duels.");
            } else {
                plugin.getLogger().warning("[duel] Realized Duels is not installed — duel features require the Duels plugin.");
            }
        });
    }
}

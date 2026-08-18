package com.sharded.core.modules.killstreaks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tracks kill streaks and runs reward commands at configured milestones. */
public final class KillstreaksModule extends Module {

    private final Map<UUID, Integer> streaks = new HashMap<>();

    public KillstreaksModule(ShardedCore plugin) {
        super(plugin, "killstreaks");
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
        streaks.clear();
    }

    public int streak(UUID uuid) {
        return streaks.getOrDefault(uuid, 0);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        streaks.put(victim.getUniqueId(), 0);

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        int streak = streaks.getOrDefault(killer.getUniqueId(), 0) + 1;
        streaks.put(killer.getUniqueId(), streak);

        ConfigurationSection rewards = config.getConfigurationSection("rewards." + streak);
        if (rewards == null) return;

        String broadcast = rewards.getString("broadcast", "");
        if (!broadcast.isEmpty()) {
            Bukkit.getServer().broadcast(Text.c(Text.apply(broadcast,
                    "%player%", killer.getName(), "%streak%", String.valueOf(streak))));
        }
        send(killer, "milestone", "%streak%", String.valueOf(streak));

        for (String cmd : rewards.getStringList("commands")) {
            cmd = cmd.replace("%player%", killer.getName())
                    .replace("%streak%", String.valueOf(streak))
                    .replace("%uuid%", killer.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }
}

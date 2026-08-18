package com.sharded.core.modules.killstreaks;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;

public final class KillstreaksModule extends Module {

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
    }

    @Override
    protected void onDisable() {
        if (database != null) database.close();
        database = null;
    }

    public KillstreakDatabase database() {
        return database;
    }

    public int streak(java.util.UUID uuid) {
        return database == null ? 0 : database.getCurrent(uuid);
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

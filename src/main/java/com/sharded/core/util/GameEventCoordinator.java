package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

/** Ensures outpost and KOTH never overlap and persist random schedules. */
public final class GameEventCoordinator {

    private static GameEventCoordinator instance;

    private final ShardedCore plugin;
    private final File file;
    private YamlConfiguration data;

    private long nextOutpostMs;
    private long nextKothMs;
    private boolean outpostActive;
    private boolean kothActive;
    private final EventBossBar bossBar;

    public GameEventCoordinator(ShardedCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "game-events.yml");
        this.bossBar = new EventBossBar(plugin);
        reload();
        instance = this;
    }

    public static GameEventCoordinator get() {
        return instance;
    }

    public void reload() {
        if (!file.exists()) {
            data = new YamlConfiguration();
            scheduleInitial();
            save();
        } else {
            data = YamlConfiguration.loadConfiguration(file);
            nextOutpostMs = data.getLong("next-outpost-ms");
            nextKothMs = data.getLong("next-koth-ms");
            if (nextOutpostMs <= 0 && nextKothMs <= 0) scheduleInitial();
        }
    }

    private void scheduleInitial() {
        long now = System.currentTimeMillis();
        nextOutpostMs = now + randomDelayMs();
        nextKothMs = now + randomDelayMs() + 3_600_000L;
    }

    private long randomDelayMs() {
        long min = 3_600_000L;
        long max = 21_600_000L;
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    public long cooldownMs() {
        return 86_400_000L;
    }

    public boolean isOutpostActive() {
        return outpostActive;
    }

    public boolean isKothActive() {
        return kothActive;
    }

    public void setOutpostActive(boolean active) {
        this.outpostActive = active;
        if (!active) {
            nextOutpostMs = System.currentTimeMillis() + cooldownMs();
            if (nextKothMs <= nextOutpostMs + 600_000L) {
                nextKothMs = nextOutpostMs + 3_600_000L;
            }
            save();
        }
    }

    public void setKothActive(boolean active) {
        this.kothActive = active;
        if (!active) {
            nextKothMs = System.currentTimeMillis() + cooldownMs();
            if (nextOutpostMs <= nextKothMs + 600_000L) {
                nextOutpostMs = nextKothMs + 3_600_000L;
            }
            save();
        }
    }

    public boolean canStartOutpost() {
        return !outpostActive && !kothActive && System.currentTimeMillis() >= nextOutpostMs;
    }

    public boolean canStartKoth() {
        return !kothActive && !outpostActive && System.currentTimeMillis() >= nextKothMs;
    }

    public long millisUntilOutpost() {
        return outpostActive ? 0 : Math.max(0, nextOutpostMs - System.currentTimeMillis());
    }

    public long millisUntilKoth() {
        return kothActive ? 0 : Math.max(0, nextKothMs - System.currentTimeMillis());
    }

    public EventBossBar bossBar() {
        return bossBar;
    }

    public void shutdown() {
        bossBar.shutdown();
    }

    public void save() {
        data.set("next-outpost-ms", nextOutpostMs);
        data.set("next-koth-ms", nextKothMs);
        try {
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save game-events.yml: " + e.getMessage());
        }
    }
}

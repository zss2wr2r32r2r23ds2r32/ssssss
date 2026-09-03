package com.shardedcore.eventcore.event;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.config.ConfigFile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The single source of truth for "what is the event doing right now".
 *
 * <p>Selections, per-mode toggles and spawn points survive restarts via
 * {@code data.yml}. Writes update the in-memory YAML immediately but the disk
 * flush is coalesced onto an async task, so an operator spamming toggles in the
 * settings menu never blocks the main thread on file IO.</p>
 */
public final class EventState {

    private final ShardedEventCore plugin;
    private final ConfigFile data;

    private final EnumMap<EventMode, EnumMap<Setting, Boolean>> toggles = new EnumMap<>(EventMode.class);
    private final EnumMap<EventMode, SpawnPoint> spawns = new EnumMap<>(EventMode.class);
    private final EnumMap<EventMode, String> selectedKits = new EnumMap<>(EventMode.class);
    private final EnumMap<EventMode, Integer> selectedCountdowns = new EnumMap<>(EventMode.class);

    private final Set<UUID> alive = new LinkedHashSet<>();
    private final Set<UUID> dead = new LinkedHashSet<>();

    private EventMode selected;
    private GamePhase phase = GamePhase.LOBBY;
    private volatile boolean saveQueued;

    public EventState(ShardedEventCore plugin, ConfigFile data) {
        this.plugin = plugin;
        this.data = data;
        load();
    }

    // ------------------------------------------------------------------ load

    public void load() {
        toggles.clear();
        spawns.clear();
        selectedKits.clear();
        selectedCountdowns.clear();

        selected = EventMode.fromId(data.raw().getString("selected-mode"));

        for (EventMode mode : EventMode.values()) {
            EnumMap<Setting, Boolean> modeToggles = new EnumMap<>(Setting.class);
            for (Setting setting : Setting.values()) {
                boolean fallback = defaultToggle(mode, setting);
                modeToggles.put(setting, data.raw().getBoolean(
                        "modes." + mode.id() + ".toggles." + setting.id(), fallback));
            }
            toggles.put(mode, modeToggles);

            SpawnPoint spawn = SpawnPoint.read(data.raw().getConfigurationSection("modes." + mode.id() + ".spawn"));
            if (spawn != null) {
                spawns.put(mode, spawn);
            }

            String kit = data.raw().getString("modes." + mode.id() + ".selected-kit");
            if (kit != null && !kit.isBlank()) {
                selectedKits.put(mode, kit);
            }

            int countdown = data.raw().getInt("modes." + mode.id() + ".selected-countdown", -1);
            if (countdown > 0) {
                selectedCountdowns.put(mode, countdown);
            }
        }
    }

    /**
     * Falls back to {@code config.yml} so an operator can decide what a fresh
     * install (or a wiped {@code data.yml}) starts with.
     */
    private boolean defaultToggle(EventMode mode, Setting setting) {
        String path = "settings.defaults." + mode.id() + "." + setting.id();
        if (plugin.mainConfig().raw().isSet(path)) {
            return plugin.mainConfig().raw().getBoolean(path);
        }
        return switch (setting) {
            case PVP, LOCATOR_BAR -> false;
            default -> true;
        };
    }

    // -------------------------------------------------------------- selection

    public EventMode selected() {
        return selected;
    }

    public boolean hasSelection() {
        return selected != null;
    }

    public boolean isSelected(EventMode mode) {
        return selected == mode;
    }

    public void select(EventMode mode) {
        this.selected = mode;
        data.raw().set("selected-mode", mode == null ? null : mode.id());
        queueSave();
    }

    // ------------------------------------------------------------------ phase

    public GamePhase phase() {
        return phase;
    }

    public void phase(GamePhase phase) {
        this.phase = phase;
    }

    /** True while players must not be able to fight, build or take damage. */
    public boolean locked() {
        return phase == GamePhase.LOBBY || phase == GamePhase.COUNTDOWN;
    }

    public boolean running() {
        return phase == GamePhase.RUNNING;
    }

    // ---------------------------------------------------------------- toggles

    public boolean toggleValue(EventMode mode, Setting setting) {
        if (mode == null) {
            return false;
        }
        EnumMap<Setting, Boolean> modeToggles = toggles.get(mode);
        Boolean value = modeToggles == null ? null : modeToggles.get(setting);
        return value != null && value;
    }

    /** Reads a toggle for the currently selected mode; false when nothing is selected. */
    public boolean toggleValue(Setting setting) {
        return selected != null && toggleValue(selected, setting);
    }

    public void setToggle(EventMode mode, Setting setting, boolean value) {
        toggles.computeIfAbsent(mode, unused -> new EnumMap<>(Setting.class)).put(setting, value);
        data.raw().set("modes." + mode.id() + ".toggles." + setting.id(), value);
        queueSave();
    }

    public boolean flipToggle(EventMode mode, Setting setting) {
        boolean next = !toggleValue(mode, setting);
        setToggle(mode, setting, next);
        return next;
    }

    // ----------------------------------------------------------------- spawns

    /**
     * Resolves the stored spawn. The world is looked up on demand so a spawn set
     * in a world that a world-management plugin loads after us is not lost.
     */
    public Location spawn(EventMode mode) {
        SpawnPoint point = spawns.get(mode);
        if (point == null) {
            return null;
        }
        Location location = point.toLocation();
        if (location == null) {
            plugin.getLogger().warning("Spawn for mode '" + mode.id() + "' points at unloaded world '"
                    + point.world() + "'.");
        }
        return location;
    }

    /** Spawn of the selected mode, or {@code null} when unset. */
    public Location activeSpawn() {
        return selected == null ? null : spawn(selected);
    }

    public void setSpawn(EventMode mode, Location location) {
        SpawnPoint point = SpawnPoint.of(location);
        spawns.put(mode, point);
        point.write(data.section("modes." + mode.id() + ".spawn"));
        queueSave();
    }

    public boolean hasSpawn(EventMode mode) {
        return spawns.containsKey(mode);
    }

    // ------------------------------------------------------- kit / countdown

    public String selectedKit(EventMode mode) {
        return selectedKits.get(mode);
    }

    public void selectedKit(EventMode mode, String kit) {
        if (kit == null) {
            selectedKits.remove(mode);
        } else {
            selectedKits.put(mode, kit);
        }
        data.raw().set("modes." + mode.id() + ".selected-kit", kit);
        queueSave();
    }

    public int selectedCountdown(EventMode mode, int fallback) {
        Integer value = selectedCountdowns.get(mode);
        return value == null ? fallback : value;
    }

    public void setSelectedCountdown(EventMode mode, int seconds) {
        selectedCountdowns.put(mode, seconds);
        data.raw().set("modes." + mode.id() + ".selected-countdown", seconds);
        queueSave();
    }

    // ------------------------------------------------------------ participants

    public Set<UUID> alive() {
        return Collections.unmodifiableSet(alive);
    }

    public Set<UUID> dead() {
        return Collections.unmodifiableSet(dead);
    }

    public int aliveCount() {
        return alive.size();
    }

    public boolean isAlive(UUID uuid) {
        return alive.contains(uuid);
    }

    public boolean isDead(UUID uuid) {
        return dead.contains(uuid);
    }

    public void markAlive(UUID uuid) {
        dead.remove(uuid);
        alive.add(uuid);
    }

    public void markDead(UUID uuid) {
        alive.remove(uuid);
        dead.add(uuid);
    }

    public void forget(UUID uuid) {
        alive.remove(uuid);
        dead.remove(uuid);
    }

    public void resetParticipants() {
        alive.clear();
        dead.clear();
    }

    /** Snapshots every online, non-spectating player as a participant. */
    public void seedParticipants() {
        resetParticipants();
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                alive.add(player.getUniqueId());
            }
        }
    }

    // --------------------------------------------------------------- persistence

    /**
     * Coalesces disk writes: the YAML tree is already up to date, so we only need
     * one flush per burst of edits and it can happen off the main thread.
     */
    private void queueSave() {
        if (saveQueued || !plugin.isEnabled()) {
            return;
        }
        saveQueued = true;
        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> {
            saveQueued = false;
            data.save();
        }, 500, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void flush() {
        saveQueued = false;
        data.save();
    }

    /** World-name based spawn record so unloaded worlds do not discard a saved spawn. */
    private record SpawnPoint(String world, double x, double y, double z, float yaw, float pitch) {

        static SpawnPoint of(Location location) {
            World world = location.getWorld();
            return new SpawnPoint(world == null ? null : world.getName(),
                    location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }

        static SpawnPoint read(ConfigurationSection section) {
            if (section == null) {
                return null;
            }
            String worldName = section.getString("world");
            if (worldName == null || worldName.isBlank()) {
                return null;
            }
            return new SpawnPoint(worldName,
                    section.getDouble("x"),
                    section.getDouble("y"),
                    section.getDouble("z"),
                    (float) section.getDouble("yaw"),
                    (float) section.getDouble("pitch"));
        }

        Location toLocation() {
            World bukkitWorld = world == null ? null : Bukkit.getWorld(world);
            return bukkitWorld == null ? null : new Location(bukkitWorld, x, y, z, yaw, pitch);
        }

        void write(ConfigurationSection section) {
            section.set("world", world);
            section.set("x", x);
            section.set("y", y);
            section.set("z", z);
            section.set("yaw", yaw);
            section.set("pitch", pitch);
        }
    }
}

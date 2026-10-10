package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.module.EventModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Resizes the world border from the settings menu.
 *
 * <p>Clicking the border icon opens a chat prompt that accepts
 * {@code <size> [duration]}, for example {@code 1000 1s} or {@code 500 15s}.
 * The size is treated as the full border width, not a radius, so typing 1000
 * gives a 1000-block border rather than 500.</p>
 *
 * <p>The shrink itself is handled by the vanilla border interpolation, which
 * costs the server nothing per tick — no scheduler task is involved.</p>
 */
public final class WorldBorderModule extends EventModule {

    /** Result of parsing the operator's chat input. */
    public record Request(double size, long millis) {
    }

    public WorldBorderModule(ShardedEventCore plugin) {
        super(plugin, "worldborder", "World border resizing, prompts and the border placeholder.");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    /** Which world the border commands act on. */
    public World targetWorld() {
        String configured = config().raw().getString("world", "");
        if (configured != null && !configured.isBlank()) {
            World named = Bukkit.getWorld(configured);
            if (named != null) {
                return named;
            }
        }
        SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
        if (spawnModule != null && spawnModule.isEnabled()) {
            Location spawn = spawnModule.resolveActiveSpawn();
            if (spawn != null && spawn.getWorld() != null) {
                return spawn.getWorld();
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    public double currentSize() {
        World world = targetWorld();
        return world == null ? 0.0D : world.getWorldBorder().getSize();
    }

    /** Border size formatted for the placeholder, e.g. {@code 1000}, {@code 999}. */
    public String formattedSize() {
        int decimals = Math.max(0, config().raw().getInt("placeholder.decimals", 0));
        double size = currentSize();
        if (decimals == 0) {
            return Long.toString(Math.round(size));
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", size);
    }

    public Location currentCentre() {
        World world = targetWorld();
        return world == null ? null : world.getWorldBorder().getCenter();
    }

    /** Applies the configured centre mode before a resize. */
    public void recentre() {
        World world = targetWorld();
        if (world == null) {
            return;
        }
        FileConfiguration config = config().raw();
        String mode = config.getString("centre.mode", "SPAWN").toUpperCase(Locale.ROOT);
        WorldBorder border = world.getWorldBorder();
        switch (mode) {
            case "FIXED" -> border.setCenter(config.getDouble("centre.x", 0.0D), config.getDouble("centre.z", 0.0D));
            case "WORLD_CENTRE", "WORLD_CENTER" -> border.setCenter(world.getSpawnLocation());
            case "SPAWN" -> {
                SpawnModule spawnModule = plugin.modules().byType(SpawnModule.class);
                Location spawn = spawnModule == null ? null : spawnModule.resolveActiveSpawn();
                border.setCenter(spawn != null && spawn.getWorld() == world ? spawn : world.getSpawnLocation());
            }
            default -> {
                // NONE: leave the existing centre alone.
            }
        }
    }

    /**
     * Sets the border width, interpolating over {@code millis} when positive.
     *
     * @return the clamped size actually applied, or {@code -1} if no world exists
     */
    public double apply(double size, long millis) {
        World world = targetWorld();
        if (world == null) {
            return -1.0D;
        }
        FileConfiguration config = config().raw();
        double clamped = Math.max(config.getDouble("min-size", 1.0D),
                Math.min(config.getDouble("max-size", 60_000_000.0D), size));

        if (config.getBoolean("recentre-on-resize", true)) {
            recentre();
        }

        WorldBorder border = world.getWorldBorder();
        border.setWarningDistance(Math.max(0, config.getInt("warning-distance", 0)));
        border.setWarningTimeTicks(Math.max(0, config.getInt("warning-time-ticks", 0)));
        border.setDamageAmount(Math.max(0.0D, config.getDouble("damage-amount", 0.2D)));
        border.setDamageBuffer(Math.max(0.0D, config.getDouble("damage-buffer", 5.0D)));

        // Vanilla interpolates in ticks, so a request is rounded to the nearest tick.
        long ticks = millis <= 0L ? 0L : Math.max(1L, Math.round(millis / 50.0D));
        if (ticks <= 0L) {
            border.setSize(clamped);
        } else {
            border.changeSize(clamped, ticks);
        }
        return clamped;
    }

    /** Applies the {@code on-event-start} border, if the operator enabled one. */
    public boolean applyEventStartBorder() {
        FileConfiguration config = config().raw();
        if (!config.getBoolean("on-event-start.enabled", false)) {
            return false;
        }
        double size = config.getDouble("on-event-start.size", 1000.0D);
        long millis = parseMillis(config.getString("on-event-start.duration", "0"), 0L);
        return apply(size, millis) >= 0.0D;
    }

    // ----------------------------------------------------------------- prompt

    /** Opens the chat prompt that asks for {@code <size> [duration]}. */
    public void promptFor(Player player) {
        long timeout = config().raw().getLong("prompt-timeout-seconds", 60L);
        plugin.messages().send(player, "worldborder.prompt",
                "%current%", formattedSize(),
                "%cancel%", plugin.mainConfig().raw().getString("prompts.cancel-word", "cancel"));

        plugin.prompts().await(player, timeout, input -> {
            Request request = parse(input);
            if (request == null) {
                plugin.messages().send(player, "worldborder.invalid", "%input%", input);
                return;
            }
            double applied = apply(request.size(), request.millis());
            if (applied < 0.0D) {
                plugin.messages().send(player, "worldborder.no-world");
                return;
            }
            plugin.messages().send(player, "worldborder.applied",
                    "%size%", Long.toString(Math.round(applied)),
                    "%duration%", formatMillis(request.millis()));
        }, () -> plugin.messages().send(player, "worldborder.cancelled"));
    }

    /**
     * Parses {@code "1000 1s"} style input.
     *
     * <p>The first token is the border width. The optional second token accepts
     * {@code ms}, {@code s}, {@code m} and {@code h} suffixes, and a bare number
     * is read as seconds.</p>
     */
    public Request parse(String input) {
        if (input == null) {
            return null;
        }
        String[] parts = input.trim().split("[\\s,]+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return null;
        }
        double size;
        try {
            size = Double.parseDouble(parts[0]);
        } catch (NumberFormatException exception) {
            return null;
        }
        if (size <= 0.0D || !Double.isFinite(size)) {
            return null;
        }
        long fallback = config().raw().getLong("default-duration-millis", 0L);
        long millis = parts.length > 1 ? parseMillis(parts[1], -1L) : fallback;
        if (millis < 0L) {
            return null;
        }
        return new Request(size, millis);
    }

    public static long parseMillis(String raw, long invalid) {
        if (raw == null || raw.isBlank()) {
            return invalid;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        long multiplier = 1000L;
        if (token.endsWith("ms")) {
            multiplier = 1L;
            token = token.substring(0, token.length() - 2);
        } else if (token.endsWith("s")) {
            token = token.substring(0, token.length() - 1);
        } else if (token.endsWith("m")) {
            multiplier = 60_000L;
            token = token.substring(0, token.length() - 1);
        } else if (token.endsWith("h")) {
            multiplier = 3_600_000L;
            token = token.substring(0, token.length() - 1);
        }
        if (token.isEmpty()) {
            return invalid;
        }
        try {
            double value = Double.parseDouble(token);
            if (value < 0.0D || !Double.isFinite(value)) {
                return invalid;
            }
            return Math.round(value * multiplier);
        } catch (NumberFormatException exception) {
            return invalid;
        }
    }

    public static String formatMillis(long millis) {
        if (millis <= 0L) {
            return "instant";
        }
        if (millis % 1000L == 0L) {
            return (millis / 1000L) + "s";
        }
        return millis + "ms";
    }
}

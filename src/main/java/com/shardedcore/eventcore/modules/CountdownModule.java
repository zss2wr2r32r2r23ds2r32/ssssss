package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.GamePhase;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.util.Feedback;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Drives every on-screen countdown.
 *
 * <p>One repeating task runs at a one-second period and only while a countdown
 * is actually live, so there is no idle scheduler cost. Titles are pushed to the
 * whole server through a single audience call instead of a per-player loop.</p>
 */
public final class CountdownModule extends EventModule {

    /** Everything a countdown needs, resolved from config once at start time. */
    private record Plan(int seconds,
                        String title,
                        String subtitle,
                        Title.Times times,
                        Map<String, String> subtitleOverrides,
                        Sound tick,
                        Sound finalTick,
                        int finalTickThreshold,
                        String finishTitle,
                        String finishSubtitle,
                        Title.Times finishTimes,
                        Sound finishSound,
                        boolean startEventOnFinish) {
    }

    private BukkitTask task;
    private Plan plan;
    private int remaining;

    public CountdownModule(ShardedEventCore plugin) {
        super(plugin, "countdown", "Configurable title countdowns via /countdown.");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    @Override
    protected void onModuleDisable() {
        cancelTask();
        plan = null;
        remaining = 0;
    }

    public boolean isRunning() {
        return task != null;
    }

    public int remaining() {
        return remaining;
    }

    public int defaultSeconds() {
        return Math.max(1, config().raw().getInt("default-seconds", 10));
    }

    /** Values offered by tab completion, e.g. {@code 3 10 15 rest}. */
    public List<String> suggestions() {
        FileConfiguration config = config().raw();
        List<String> out = new ArrayList<>();
        for (int value : config.getIntegerList("tab-complete")) {
            out.add(Integer.toString(value));
        }
        ConfigurationSection presets = config.getConfigurationSection("presets");
        if (presets != null) {
            out.addAll(presets.getKeys(false));
        }
        out.add("stop");
        return out;
    }

    public boolean hasPreset(String id) {
        return id != null && config().raw().isConfigurationSection("presets." + id.toLowerCase(Locale.ROOT));
    }

    /** Starts a named preset such as {@code rest}. */
    public boolean startPreset(String id) {
        String key = id.toLowerCase(Locale.ROOT);
        ConfigurationSection preset = config().raw().getConfigurationSection("presets." + key);
        if (preset == null) {
            return false;
        }
        return start(resolve(preset, preset.getInt("seconds", defaultSeconds())));
    }

    /** Starts a plain numeric countdown, honouring a matching preset override. */
    public boolean start(int seconds) {
        if (seconds <= 0) {
            return false;
        }
        ConfigurationSection preset = config().raw()
                .getConfigurationSection("presets." + Integer.toString(seconds));
        return start(resolve(preset, seconds));
    }

    private boolean start(Plan resolved) {
        if (resolved == null || resolved.seconds() <= 0) {
            return false;
        }
        cancelTask();
        this.plan = resolved;
        this.remaining = resolved.seconds();
        plugin.state().phase(GamePhase.COUNTDOWN);
        this.task = track(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 20L));
        return true;
    }

    public void stop(boolean clearTitles) {
        boolean wasRunning = isRunning();
        cancelTask();
        remaining = 0;
        plan = null;
        if (clearTitles) {
            Feedback.clearTitles();
        }
        if (wasRunning && plugin.state().phase() == GamePhase.COUNTDOWN) {
            plugin.state().phase(GamePhase.LOBBY);
        }
    }

    private void cancelTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        Plan current = plan;
        if (current == null) {
            cancelTask();
            return;
        }
        if (remaining <= 0) {
            finish(current);
            return;
        }

        String seconds = Integer.toString(remaining);
        String subtitle = current.subtitleOverrides().getOrDefault(seconds, current.subtitle());
        Map<String, String> placeholders = Map.of("%seconds%", seconds, "%time%", seconds);

        Feedback.broadcastTitle(current.title(), subtitle, current.times(), placeholders);
        Feedback.play(Bukkit.getServer(),
                remaining <= current.finalTickThreshold() && current.finalTick() != null
                        ? current.finalTick() : current.tick());

        remaining--;
    }

    private void finish(Plan current) {
        cancelTask();
        plan = null;
        remaining = 0;

        Feedback.broadcastTitle(current.finishTitle(), current.finishSubtitle(), current.finishTimes(), Map.of());
        Feedback.play(Bukkit.getServer(), current.finishSound());

        if (current.startEventOnFinish()) {
            GameModule game = plugin.modules().byType(GameModule.class);
            if (game != null && game.isEnabled()) {
                game.unlock();
                return;
            }
        }
        if (plugin.state().phase() == GamePhase.COUNTDOWN) {
            plugin.state().phase(GamePhase.LOBBY);
        }
    }

    /**
     * Builds a plan by layering an optional preset section over the module-level
     * defaults, so an operator only has to override what differs.
     */
    private Plan resolve(ConfigurationSection preset, int seconds) {
        FileConfiguration config = config().raw();

        String title = pick(preset, config, "title", "&#AD4EFF&l%seconds%");
        String subtitle = pick(preset, config, "subtitle", "&fRe organise your Inventory");

        ConfigurationSection timesSection = section(preset, config, "times");
        Title.Times times = Feedback.times(timesSection, 0, 25, 5);

        ConfigurationSection overridesSection = section(preset, config, "subtitle-overrides");
        Map<String, String> overrides = readOverrides(overridesSection);

        ConfigurationSection soundSection = section(preset, config, "sound");
        Sound tick = Feedback.sound(soundSection == null ? null : soundSection.getConfigurationSection("tick"));
        Sound finalTick = Feedback.sound(soundSection == null ? null : soundSection.getConfigurationSection("final-tick"));
        Sound finishSound = Feedback.sound(soundSection == null ? null : soundSection.getConfigurationSection("finish"));
        int threshold = soundSection == null ? 3 : soundSection.getInt("final-tick-threshold", 3);

        ConfigurationSection finishSection = section(preset, config, "finish");
        String finishTitle = finishSection == null ? "" : finishSection.getString("title", "");
        String finishSubtitle = finishSection == null ? "" : finishSection.getString("subtitle", "");
        Title.Times finishTimes = Feedback.times(
                finishSection == null ? null : finishSection.getConfigurationSection("times"), 0, 30, 10);

        boolean startEvent = preset != null && preset.isSet("start-event-on-finish")
                ? preset.getBoolean("start-event-on-finish")
                : config.getBoolean("start-event-on-finish", true);

        return new Plan(seconds, title, subtitle, times, overrides, tick, finalTick, threshold,
                finishTitle, finishSubtitle, finishTimes, finishSound, startEvent);
    }

    private static Map<String, String> readOverrides(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        java.util.Map<String, String> out = new java.util.HashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value != null) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static String pick(ConfigurationSection preset, FileConfiguration config, String key, String fallback) {
        if (preset != null && preset.isSet(key)) {
            return preset.getString(key, fallback);
        }
        return config.getString(key, fallback);
    }

    private static ConfigurationSection section(ConfigurationSection preset, FileConfiguration config, String key) {
        if (preset != null && preset.isConfigurationSection(key)) {
            return preset.getConfigurationSection(key);
        }
        return config.getConfigurationSection(key);
    }
}

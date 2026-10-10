package com.shardedcore.eventcore.module;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.config.ConfigFile;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

/**
 * A self-contained feature with its own {@code modules/<id>.yml} file.
 *
 * <p>Listeners and repeating tasks are only ever attached while the module is
 * enabled. That is the main reason the feature set is split up this way: a
 * disabled module costs literally nothing at runtime, because Bukkit never even
 * walks a handler list entry for it.</p>
 */
public abstract class EventModule implements Listener {

    protected final ShardedEventCore plugin;
    private final String id;
    private final String description;
    private final List<BukkitTask> tasks = new ArrayList<>(2);

    private ConfigFile config;
    private boolean enabled;

    protected EventModule(ShardedEventCore plugin, String id, String description) {
        this.plugin = plugin;
        this.id = id;
        this.description = description;
    }

    public final String id() {
        return id;
    }

    public final String description() {
        return description;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    /** Lazily created so a module with no options of its own still gets a file. */
    public final ConfigFile config() {
        if (config == null) {
            config = new ConfigFile(plugin, "modules/" + id + ".yml");
            if (!config.raw().isSet("enabled")) {
                config.raw().set("enabled", true);
                config.save();
            }
        }
        return config;
    }

    /** Whether {@code modules/<id>.yml} asks for this module to run. */
    public final boolean configuredEnabled() {
        return config().raw().getBoolean("enabled", true);
    }

    public final void persistEnabled(boolean value) {
        config().raw().set("enabled", value);
        config().save();
    }

    final void enableInternal() {
        if (enabled) {
            return;
        }
        enabled = true;
        if (hasListeners()) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
        onModuleEnable();
    }

    final void disableInternal() {
        if (!enabled) {
            return;
        }
        enabled = false;
        HandlerList.unregisterAll(this);
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
        onModuleDisable();
    }

    final void reloadInternal() {
        config().reload();
        onConfigReload();
    }

    /**
     * Modules that are pure command handlers can skip listener registration
     * entirely by overriding this to {@code false}.
     */
    protected boolean hasListeners() {
        return true;
    }

    protected void onModuleEnable() {
    }

    protected void onModuleDisable() {
    }

    protected void onConfigReload() {
    }

    /** Registers a task that is automatically cancelled when the module stops. */
    protected final BukkitTask track(BukkitTask task) {
        tasks.add(task);
        return task;
    }
}

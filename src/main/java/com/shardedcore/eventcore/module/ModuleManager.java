package com.shardedcore.eventcore.module;

import com.shardedcore.eventcore.ShardedEventCore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Registry that owns the lifecycle of every {@link EventModule}. */
public final class ModuleManager {

    private final ShardedEventCore plugin;
    private final Map<String, EventModule> modules = new LinkedHashMap<>();
    private final Map<Class<? extends EventModule>, EventModule> byType = new LinkedHashMap<>();

    public ModuleManager(ShardedEventCore plugin) {
        this.plugin = plugin;
    }

    public void register(EventModule module) {
        modules.put(module.id().toLowerCase(Locale.ROOT), module);
        byType.put(module.getClass(), module);
    }

    /** Starts every module whose {@code modules/<id>.yml} has {@code enabled: true}. */
    public void startAll() {
        for (EventModule module : modules.values()) {
            if (module.configuredEnabled()) {
                try {
                    module.enableInternal();
                } catch (RuntimeException exception) {
                    plugin.getLogger().severe("Module '" + module.id() + "' failed to start: " + exception);
                    module.disableInternal();
                }
            }
        }
    }

    public void stopAll() {
        List<EventModule> reversed = new ArrayList<>(modules.values());
        java.util.Collections.reverse(reversed);
        for (EventModule module : reversed) {
            try {
                module.disableInternal();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Module '" + module.id() + "' failed to stop cleanly: " + exception);
            }
        }
    }

    /** Re-reads every module config, restarting the ones whose enabled flag changed. */
    public void reloadAll() {
        for (EventModule module : modules.values()) {
            module.reloadInternal();
            boolean shouldRun = module.configuredEnabled();
            if (shouldRun && !module.isEnabled()) {
                module.enableInternal();
            } else if (!shouldRun && module.isEnabled()) {
                module.disableInternal();
            }
        }
    }

    /**
     * Applies a runtime enable/disable and persists it.
     *
     * @return {@code true} when the state actually changed
     */
    public boolean setEnabled(EventModule module, boolean value) {
        if (module.isEnabled() == value) {
            module.persistEnabled(value);
            return false;
        }
        if (value) {
            module.enableInternal();
        } else {
            module.disableInternal();
        }
        module.persistEnabled(value);
        return true;
    }

    public EventModule byId(String id) {
        return id == null ? null : modules.get(id.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("unchecked")
    public <T extends EventModule> T byType(Class<T> type) {
        return (T) byType.get(type);
    }

    public Collection<EventModule> all() {
        return modules.values();
    }

    public List<String> ids() {
        return new ArrayList<>(modules.keySet());
    }
}

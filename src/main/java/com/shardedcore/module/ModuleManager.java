package com.shardedcore.module;

import com.shardedcore.ShardedCore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class ModuleManager {

    private final ShardedCore plugin;
    private final Map<String, Module> registered = new LinkedHashMap<>();
    private final Map<String, Module> enabled = new LinkedHashMap<>();

    public ModuleManager(ShardedCore plugin) {
        this.plugin = plugin;
    }

    public void register(Module module) {
        registered.put(module.getId(), module);
    }

    public void loadAll() {
        disableAll();
        for (Module module : registered.values()) {
            if (!module.isEnabledInConfig()) {
                plugin.getLogger().info("Module disabled in config: " + module.getId());
                continue;
            }
            try {
                module.loadFiles();
                module.enable();
                enabled.put(module.getId(), module);
                plugin.getLogger().info("Enabled module: " + module.getId());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + module.getId(), ex);
            }
        }
    }

    public void reloadAll() {
        for (Module module : new ArrayList<>(enabled.values())) {
            try {
                module.reload();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to reload module: " + module.getId(), ex);
            }
        }
        loadAll();
    }

    public void disableAll() {
        List<Module> modules = new ArrayList<>(enabled.values());
        Collections.reverse(modules);
        for (Module module : modules) {
            try {
                module.disable();
            } catch (Exception ex) {
                plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + module.getId(), ex);
            }
        }
        enabled.clear();
    }

    public Collection<Module> getRegistered() {
        return Collections.unmodifiableCollection(registered.values());
    }

    public Collection<Module> getEnabled() {
        return Collections.unmodifiableCollection(enabled.values());
    }

    public Module getModule(String id) {
        return enabled.get(id);
    }

    public boolean isEnabled(String id) {
        return enabled.containsKey(id);
    }
}

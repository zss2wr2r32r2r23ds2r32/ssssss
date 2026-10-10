package com.shardedcore.eventcore.modules;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.event.EventMode;
import com.shardedcore.eventcore.event.Setting;
import com.shardedcore.eventcore.module.EventModule;
import com.shardedcore.eventcore.placeholder.PlaceholderBridge;

import java.util.Locale;

/**
 * Exposes the event state to other plugins.
 *
 * <p>Registration is guarded so the plugin runs perfectly well without
 * PlaceholderAPI: the bridge class that touches the PlaceholderAPI types is only
 * ever loaded once the plugin has been confirmed present.</p>
 */
public final class PlaceholderModule extends EventModule {

    private boolean registered;

    public PlaceholderModule(ShardedEventCore plugin) {
        super(plugin, "placeholders", "PlaceholderAPI expansion (%shardedcore_alive%, %shardedcore_border%, ...).");
    }

    @Override
    protected boolean hasListeners() {
        return false;
    }

    @Override
    protected void onModuleEnable() {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("PlaceholderAPI not found; %shardedcore_...% placeholders are unavailable.");
            return;
        }
        registered = PlaceholderBridge.register(plugin, this);
        if (registered) {
            plugin.getLogger().info("Registered the shardedcore PlaceholderAPI expansion.");
        }
    }

    @Override
    protected void onModuleDisable() {
        if (registered) {
            PlaceholderBridge.unregister();
            registered = false;
        }
    }

    /**
     * Resolves one placeholder token (everything after {@code shardedcore_}).
     *
     * @return the value, or {@code null} when the token is unknown
     */
    public String resolve(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        String key = token.toLowerCase(Locale.ROOT);
        return switch (key) {
            case "alive" -> Integer.toString(plugin.state().aliveCount());
            case "dead" -> Integer.toString(plugin.state().dead().size());
            case "total" -> Integer.toString(plugin.state().aliveCount() + plugin.state().dead().size());
            case "border" -> border();
            case "border_centre_x", "border_center_x" -> borderCentre(true);
            case "border_centre_z", "border_center_z" -> borderCentre(false);
            case "mode" -> plugin.state().hasSelection() ? plugin.state().selected().id() : "none";
            case "phase" -> plugin.state().phase().name().toLowerCase(Locale.ROOT);
            case "running" -> Boolean.toString(plugin.state().running());
            case "countdown" -> countdown();
            case "tracked_blocks" -> trackedBlocks();
            default -> resolvePrefixed(key);
        };
    }

    private String resolvePrefixed(String key) {
        if (key.startsWith("setting_")) {
            Setting setting = Setting.fromId(key.substring("setting_".length()));
            if (setting == null) {
                return null;
            }
            return Boolean.toString(plugin.state().toggleValue(setting));
        }
        if (key.startsWith("spawn_set_")) {
            EventMode mode = EventMode.fromId(key.substring("spawn_set_".length()));
            return mode == null ? null : Boolean.toString(plugin.state().hasSpawn(mode));
        }
        if (key.startsWith("kit_")) {
            EventMode mode = EventMode.fromId(key.substring("kit_".length()));
            if (mode == null) {
                return null;
            }
            String kit = plugin.state().selectedKit(mode);
            return kit == null ? "none" : kit;
        }
        return null;
    }

    private String border() {
        WorldBorderModule module = plugin.modules().byType(WorldBorderModule.class);
        return module == null || !module.isEnabled() ? "0" : module.formattedSize();
    }

    private String borderCentre(boolean xAxis) {
        WorldBorderModule module = plugin.modules().byType(WorldBorderModule.class);
        if (module == null || !module.isEnabled()) {
            return "0";
        }
        org.bukkit.Location centre = module.currentCentre();
        if (centre == null) {
            return "0";
        }
        return Long.toString(Math.round(xAxis ? centre.getX() : centre.getZ()));
    }

    private String countdown() {
        CountdownModule module = plugin.modules().byType(CountdownModule.class);
        return module == null || !module.isEnabled() ? "0" : Integer.toString(module.remaining());
    }

    private String trackedBlocks() {
        ClearBlocksModule module = plugin.modules().byType(ClearBlocksModule.class);
        return module == null || !module.isEnabled() ? "0" : Integer.toString(module.trackedCount());
    }
}

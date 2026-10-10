package com.shardedcore.eventcore.placeholder;

import com.shardedcore.eventcore.ShardedEventCore;
import com.shardedcore.eventcore.modules.PlaceholderModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * Isolation layer for the PlaceholderAPI dependency.
 *
 * <p>This class is the only place that references PlaceholderAPI types, and it is
 * loaded lazily from {@link PlaceholderModule} after the plugin has been found.
 * Servers without PlaceholderAPI therefore never load it and never hit a
 * {@code NoClassDefFoundError}.</p>
 */
public final class PlaceholderBridge {

    private static Expansion active;

    private PlaceholderBridge() {
    }

    public static boolean register(ShardedEventCore plugin, PlaceholderModule module) {
        if (active != null) {
            return true;
        }
        Expansion expansion = new Expansion(plugin, module);
        if (!expansion.register()) {
            return false;
        }
        active = expansion;
        return true;
    }

    public static void unregister() {
        if (active != null) {
            active.unregister();
            active = null;
        }
    }

    private static final class Expansion extends PlaceholderExpansion {

        private final ShardedEventCore plugin;
        private final PlaceholderModule module;

        private Expansion(ShardedEventCore plugin, PlaceholderModule module) {
            this.plugin = plugin;
            this.module = module;
        }

        @Override
        public String getIdentifier() {
            return "shardedcore";
        }

        @Override
        public String getAuthor() {
            return String.join(", ", plugin.getPluginMeta().getAuthors());
        }

        @Override
        public String getVersion() {
            return plugin.getPluginMeta().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            return module.resolve(params);
        }
    }
}

package com.sharded.core.net;

import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.logging.Logger;

/**
 * Explains the add_entity kick when known-incompatible plugins are present.
 */
public final class PluginCompatibility {
    private PluginCompatibility() {
    }

    public static void warn(Plugin plugin) {
        Logger log = plugin.getLogger();
        Plugin eglow = plugin.getServer().getPluginManager().getPlugin("eGlow");
        if (eglow != null && eglow.isEnabled()) {
            String version = eglow.getPluginMeta().getVersion();
            log.warning("eGlow " + version + " is loaded. The jar on this server is built for 1.21.9 or older,");
            log.warning("but Paper 1.21.11 requires a non-null velocity on every add_entity packet.");
            log.warning("That mismatch kicks players with: Failed to encode packet 'clientbound/minecraft:add_entity'.");
            log.warning("ShardedCore will fill in 0,0,0 when velocity is missing. Still update or remove eGlow.");
        }
        Plugin protocolLib = plugin.getServer().getPluginManager().getPlugin("ProtocolLib");
        if (protocolLib != null && protocolLib.isEnabled()) {
            String version = protocolLib.getPluginMeta().getVersion();
            log.info("ProtocolLib " + version + " is loaded. Older builds drop spawn-packet velocity on 1.21.9+;");
            log.info("ShardedCore patches null velocity before the packet is encoded.");
            if (looksOldProtocolLib(version)) {
                log.warning("ProtocolLib " + version + " may be too old for 1.21.11. Use a 5.5.0+ development build.");
            }
        }
        Plugin tab = plugin.getServer().getPluginManager().getPlugin("TAB");
        if (tab != null && tab.isEnabled()) {
            log.info("TAB " + tab.getPluginMeta().getVersion() + " is loaded (also injects into the Netty pipeline).");
        }
    }

    static boolean looksOldProtocolLib(String version) {
        if (version == null) {
            return false;
        }
        String lower = version.toLowerCase(Locale.ROOT);
        return lower.startsWith("4.") || lower.startsWith("5.0") || lower.startsWith("5.1")
                || lower.startsWith("5.2") || lower.startsWith("5.3") || lower.startsWith("5.4.0");
    }
}

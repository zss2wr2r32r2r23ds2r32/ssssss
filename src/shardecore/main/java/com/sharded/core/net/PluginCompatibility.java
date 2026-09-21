package com.sharded.core.net;

import java.util.Locale;
import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public final class PluginCompatibility {
   private PluginCompatibility() {
   }

   public static void warn(Plugin plugin) {
      Logger logger = plugin.getLogger();
      Plugin pluginx = plugin.getServer().getPluginManager().getPlugin("eGlow");
      if (pluginx != null && pluginx.isEnabled()) {
         String s = pluginx.getPluginMeta().getVersion();
         logger.warning("eGlow " + s + " is loaded. The jar on this server is built for 1.21.9 or older,");
         logger.warning("but Paper 1.21.11 requires a non-null velocity on every add_entity packet.");
         logger.warning("That mismatch kicks players with: Failed to encode packet 'clientbound/minecraft:add_entity'.");
         logger.warning("ShardedCore will fill in 0,0,0 when velocity is missing. Still update or remove eGlow.");
      }

      Plugin plugin1 = plugin.getServer().getPluginManager().getPlugin("ProtocolLib");
      if (plugin1 != null && plugin1.isEnabled()) {
         String s1 = plugin1.getPluginMeta().getVersion();
         logger.info("ProtocolLib " + s1 + " is loaded. Older builds drop spawn-packet velocity on 1.21.9+;");
         logger.info("ShardedCore patches null velocity before the packet is encoded.");
         if (looksOldProtocolLib(s1)) {
            logger.warning("ProtocolLib " + s1 + " may be too old for 1.21.11. Use a 5.5.0+ development build.");
         }
      }

      Plugin plugin2 = plugin.getServer().getPluginManager().getPlugin("TAB");
      if (plugin2 != null && plugin2.isEnabled()) {
         logger.info("TAB " + plugin2.getPluginMeta().getVersion() + " is loaded (also injects into the Netty pipeline).");
      }
   }

   static boolean looksOldProtocolLib(String version) {
      if (version == null) {
         return false;
      } else {
         String s = version.toLowerCase(Locale.ROOT);
         return s.startsWith("4.") || s.startsWith("5.0") || s.startsWith("5.1") || s.startsWith("5.2") || s.startsWith("5.3") || s.startsWith("5.4.0");
      }
   }
}

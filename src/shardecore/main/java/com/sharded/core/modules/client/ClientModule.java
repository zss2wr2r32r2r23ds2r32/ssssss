package com.sharded.core.modules.client;

import com.sharded.core.ShardedCore;
import com.sharded.core.module.Module;
import com.sharded.core.util.ItemsAdderEscMenuSync;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ClientModule extends Module implements PluginMessageListener {
   private final Set<UUID> lunarPlayers = ConcurrentHashMap.newKeySet();
   private boolean featherApiAvailable;

   public ClientModule(ShardedCore plugin) {
      super(plugin, "client");
   }

   @Override
   protected void onEnable() {
      ItemsAdderEscMenuSync.install(this.plugin, this.config);
      this.featherApiAvailable = Bukkit.getPluginManager().getPlugin("feather-server-api") != null;
      this.registerPluginChannels();
   }

   private void registerPluginChannels() {
      if (this.config.getBoolean("discord-rpc.enabled", true)) {
         Messenger messenger = Bukkit.getMessenger();
         messenger.registerOutgoingPluginChannel(this.plugin, "apollo:json");
         messenger.registerIncomingPluginChannel(this.plugin, "lunar:apollo", this);
      }
   }

   @Override
   protected void onDisable() {
      Messenger messenger = Bukkit.getMessenger();

      try {
         messenger.unregisterIncomingPluginChannel(this.plugin, "lunar:apollo", this);
         messenger.unregisterOutgoingPluginChannel(this.plugin, "apollo:json");
      } catch (Exception exception) {
      }

      this.lunarPlayers.clear();
   }

   @EventHandler(
      priority = EventPriority.MONITOR
   )
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline()) {
            this.applyDiscordPresence(player);
         }
      }, this.config.getLong("apply-delay-ticks", 40L));
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lunarPlayers.remove(event.getPlayer().getUniqueId());
   }

   public void onPluginMessageReceived(String channel, Player player, byte[] message) {
      if ("lunar:apollo".equals(channel)) {
         this.lunarPlayers.add(player.getUniqueId());
         Bukkit.getScheduler().runTask(this.plugin, () -> this.applyDiscordPresence(player));
      }
   }

   private void applyDiscordPresence(Player player) {
      if (this.config.getBoolean("discord-rpc.enabled", true)) {
         if (!this.tryFeatherPresence(player)) {
            if (!this.tryApolloReflection(player)) {
               this.sendLunarJsonPresence(player);
            }
         }
      }
   }

   private boolean tryFeatherPresence(Player player) {
      if (!this.featherApiAvailable) {
         return false;
      } else {
         try {
            Class<?> oclass = Class.forName("net.digitalingot.feather.serverapi.api.FeatherAPI");
            Object object = oclass.getMethod("getMetaService").invoke(null);
            Class<?> oclass1 = Class.forName("net.digitalingot.feather.serverapi.api.meta.DiscordActivity");
            Object object1 = oclass1.getMethod("builder").invoke(null);
            String s = this.config.getString("discord-rpc.details", "Playing Sharded MC");
            String s1 = this.config.getString("discord-rpc.state", "Online on ShardedMC");
            String s2 = this.config.getString("discord-rpc.image-url", "");
            String s3 = this.config.getString("discord-rpc.image-text", "Sharded MC");
            this.invokeBuilder(object1, "withDetails", s);
            this.invokeBuilder(object1, "withState", s1);
            if (s2 != null && !s2.isBlank()) {
               this.invokeBuilder(object1, "withImage", s2);
               this.invokeBuilder(object1, "withImageText", s3);
            }

            if (this.config.getBoolean("discord-rpc.show-player-count", true)) {
               int i = Bukkit.getOnlinePlayers().size();
               int j = Bukkit.getMaxPlayers();
               this.invokeBuilder(object1, "withPartySize", i, j);
            }

            Object object3 = object1.getClass().getMethod("build").invoke(object1);
            Class<?> oclass2 = Class.forName("net.digitalingot.feather.serverapi.api.player.FeatherPlayer");
            Object object2 = oclass.getMethod("getPlayer", UUID.class).invoke(null, player.getUniqueId());
            if (object2 == null) {
               return false;
            } else {
               object.getClass().getMethod("updateDiscordActivity", oclass2, oclass1).invoke(object, object2, object3);
               return true;
            }
         } catch (ClassNotFoundException classnotfoundexception) {
            this.featherApiAvailable = false;
            return false;
         } catch (Exception exception) {
            this.plugin.getLogger().fine("[client] Feather RPC skipped for " + player.getName() + ": " + exception.getMessage());
            return false;
         }
      }
   }

   private void invokeBuilder(Object builder, String method, Object... args) throws Exception {
      Class<?>[] oclass = new Class[args.length];

      for (int i = 0; i < args.length; i++) {
         oclass[i] = args[i] instanceof Integer ? int.class : args[i].getClass();
      }

      builder.getClass().getMethod(method, oclass).invoke(builder, args);
   }

   private boolean tryApolloReflection(Player player) {
      if (Bukkit.getPluginManager().getPlugin("Apollo-Bukkit") == null) {
         return false;
      } else {
         try {
            Class<?> oclass = Class.forName("com.lunarclient.apollo.Apollo");
            Object object = oclass.getMethod("getPlayerManager").invoke(null);
            if (object.getClass().getMethod("getPlayer", UUID.class).invoke(object, player.getUniqueId()) instanceof Optional<?> optional
               && !optional.isEmpty()) {
               Object object1 = optional.get();
               Class<?> oclass1 = Class.forName("com.lunarclient.apollo.module.richpresence.ServerRichPresence");
               Object object2 = oclass1.getMethod("builder").invoke(null);
               this.setPresenceField(object2, "gameName", this.config.getString("discord-rpc.game-name", "Sharded MC"));
               this.setPresenceField(object2, "gameState", this.config.getString("discord-rpc.state", "Online"));
               this.setPresenceField(object2, "playerState", this.config.getString("discord-rpc.details", "Playing Sharded MC"));
               Object object3 = object2.getClass().getMethod("build").invoke(object2);
               Object object4 = oclass.getMethod("getModuleManager").invoke(null);
               Object object5 = object4.getClass()
                  .getMethod("getModule", Class.class)
                  .invoke(object4, Class.forName("com.lunarclient.apollo.module.richpresence.RichPresenceModule"));
               object5.getClass()
                  .getMethod("overrideServerRichPresence", Class.forName("com.lunarclient.apollo.player.ApolloPlayer"), oclass1)
                  .invoke(object5, object1, object3);
               return true;
            }

            return false;
         } catch (Exception exception) {
            this.plugin.getLogger().fine("[client] Apollo RPC skipped for " + player.getName() + ": " + exception.getMessage());
            return false;
         }
      }
   }

   private void setPresenceField(Object builder, String method, String value) throws Exception {
      if (value != null && !value.isBlank()) {
         builder.getClass().getMethod(method, String.class).invoke(builder, value);
      }
   }

   private void sendLunarJsonPresence(Player player) {
      if (this.lunarPlayers.contains(player.getUniqueId()) || this.config.getBoolean("discord-rpc.send-without-detection", true)) {
         try {
            String s = escapeJson(this.config.getString("discord-rpc.game-name", "Sharded MC"));
            String s1 = escapeJson(this.config.getString("discord-rpc.state", "Online on ShardedMC"));
            String s2 = escapeJson(this.config.getString("discord-rpc.details", "Playing Sharded MC"));
            String s3 = "{\"@type\":\"type.googleapis.com/lunarclient.apollo.richpresence.v1.OverrideServerRichPresenceMessage\",\"game_name\":\"%s\",\"game_state\":\"%s\",\"player_state\":\"%s\"}\n"
               .formatted(s, s1, s2)
               .trim();
            player.sendPluginMessage(this.plugin, "apollo:json", s3.getBytes(StandardCharsets.UTF_8));
         } catch (Exception exception) {
            this.plugin.getLogger().fine("[client] Lunar JSON RPC failed for " + player.getName() + ": " + exception.getMessage());
         }
      }
   }

   private static String escapeJson(String value) {
      return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
   }
}

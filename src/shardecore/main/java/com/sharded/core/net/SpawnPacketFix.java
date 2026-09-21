package com.sharded.core.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.channel.ChannelHandler.Sharable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class SpawnPacketFix implements Listener {
   private static final Key LISTENER_KEY = Key.key("shardedcore", "add_entity_fix");
   private static SpawnPacketFix instance;
   private final JavaPlugin plugin;
   private final SpawnPacketFix.FixHandler handler;
   private boolean channelInitRegistered;

   private SpawnPacketFix(JavaPlugin plugin, AddEntityPacketPatcher patcher) {
      this.plugin = plugin;
      this.handler = new SpawnPacketFix.FixHandler(patcher);
   }

   public static void install(JavaPlugin plugin) {
      uninstall();
      AddEntityPacketPatcher addentitypacketpatcher = AddEntityPacketPatcher.tryCreate(plugin.getLogger());
      if (addentitypacketpatcher != null) {
         SpawnPacketFix spawnpacketfix = new SpawnPacketFix(plugin, addentitypacketpatcher);
         instance = spawnpacketfix;
         PluginCompatibility.warn(plugin);
         plugin.getServer().getPluginManager().registerEvents(spawnpacketfix, plugin);
         spawnpacketfix.registerChannelInit();

         for (Player player : Bukkit.getOnlinePlayers()) {
            spawnpacketfix.injectPlayer(player);
         }
      }
   }

   public static void uninstall() {
      if (instance != null) {
         instance.unregisterChannelInit();
         HandlerList.unregisterAll(instance);

         for (Player player : Bukkit.getOnlinePlayers()) {
            Channel channel = ChannelLookup.of(player);
            if (channel != null) {
               instance.remove(channel);
            }
         }

         instance = null;
      }
   }

   @EventHandler(
      priority = EventPriority.LOWEST
   )
   public void onJoin(PlayerJoinEvent event) {
      this.injectPlayer(event.getPlayer());
   }

   private void registerChannelInit() {
      try {
         Class<?> oclass = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
         Class<?> oclass1 = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
         Object object = Proxy.newProxyInstance(oclass1.getClassLoader(), new Class[]{oclass1}, (unused, method, args) -> {
            if ("afterInitChannel".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Channel channel) {
               this.inject(channel);
            }

            return null;
         });
         Method method = oclass.getMethod("addListener", Key.class, oclass1);
         method.invoke(null, LISTENER_KEY, object);
         this.channelInitRegistered = true;
      } catch (Throwable throwable) {
         this.plugin.getLogger().info("Channel init listener unavailable; spawn-packet patcher will inject on player join.");
      }
   }

   private void unregisterChannelInit() {
      if (this.channelInitRegistered) {
         try {
            Class<?> oclass = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            oclass.getMethod("removeListener", Key.class).invoke(null, LISTENER_KEY);
         } catch (Throwable throwable) {
         }

         this.channelInitRegistered = false;
      }
   }

   private void injectPlayer(Player player) {
      Channel channel = ChannelLookup.of(player);
      if (channel != null) {
         Runnable runnable = () -> this.inject(channel);
         if (channel.eventLoop().inEventLoop()) {
            runnable.run();
         } else {
            channel.eventLoop().execute(runnable);
         }
      }
   }

   private void inject(Channel channel) {
      if (channel != null && channel.isOpen()) {
         try {
            ChannelPipeline channelpipeline = channel.pipeline();
            if (channelpipeline.get("shardedcore_add_entity_fix") != null) {
               channelpipeline.remove("shardedcore_add_entity_fix");
            }

            String s = PipelineNames.encoderName(List.copyOf(channelpipeline.names()));
            if (s != null && channelpipeline.get(s) != null) {
               channelpipeline.addBefore(s, "shardedcore_add_entity_fix", this.handler);
            } else {
               channelpipeline.addLast("shardedcore_add_entity_fix", this.handler);
            }
         } catch (Exception exception) {
            this.plugin.getLogger().fine("Could not inject add_entity patcher into a connection: " + exception.getMessage());
         }
      }
   }

   private void remove(Channel channel) {
      if (channel != null) {
         Runnable runnable = () -> {
            try {
               if (channel.pipeline().get("shardedcore_add_entity_fix") != null) {
                  channel.pipeline().remove("shardedcore_add_entity_fix");
               }
            } catch (Exception exception) {
            }
         };
         if (channel.eventLoop().inEventLoop()) {
            runnable.run();
         } else {
            channel.eventLoop().execute(runnable);
         }
      }
   }

   @Sharable
   private static final class FixHandler extends ChannelDuplexHandler {
      private final AddEntityPacketPatcher patcher;

      private FixHandler(AddEntityPacketPatcher patcher) {
         this.patcher = patcher;
      }

      public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
         super.write(ctx, this.patcher.patch(msg), promise);
      }
   }
}

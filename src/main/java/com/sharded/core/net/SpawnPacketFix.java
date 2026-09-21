package com.sharded.core.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * Injects a late outbound handler that rewrites null add_entity velocity to zero.
 */
public final class SpawnPacketFix implements Listener {
    private static final Key LISTENER_KEY = Key.key("shardedcore", "add_entity_fix");
    private static SpawnPacketFix instance;

    private final JavaPlugin plugin;
    private final FixHandler handler;
    private boolean channelInitRegistered;

    private SpawnPacketFix(JavaPlugin plugin, AddEntityPacketPatcher patcher) {
        this.plugin = plugin;
        this.handler = new FixHandler(patcher);
    }

    public static void install(JavaPlugin plugin) {
        uninstall();
        AddEntityPacketPatcher patcher = AddEntityPacketPatcher.tryCreate(plugin.getLogger());
        if (patcher == null) {
            return;
        }
        SpawnPacketFix fix = new SpawnPacketFix(plugin, patcher);
        instance = fix;
        PluginCompatibility.warn(plugin);
        plugin.getServer().getPluginManager().registerEvents(fix, plugin);
        fix.registerChannelInit();
        for (Player player : Bukkit.getOnlinePlayers()) {
            fix.injectPlayer(player);
        }
    }

    public static void uninstall() {
        if (instance == null) {
            return;
        }
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

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        injectPlayer(event.getPlayer());
    }

    private void registerChannelInit() {
        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> listener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (unused, method, args) -> {
                if ("afterInitChannel".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Channel channel) {
                    inject(channel);
                }
                return null;
            });
            Method add = holder.getMethod("addListener", Key.class, listener);
            add.invoke(null, LISTENER_KEY, proxy);
            channelInitRegistered = true;
        } catch (Throwable ex) {
            plugin.getLogger().info("Channel init listener unavailable; spawn-packet patcher will inject on player join.");
        }
    }

    private void unregisterChannelInit() {
        if (!channelInitRegistered) {
            return;
        }
        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            holder.getMethod("removeListener", Key.class).invoke(null, LISTENER_KEY);
        } catch (Throwable ignored) {
        }
        channelInitRegistered = false;
    }

    private void injectPlayer(Player player) {
        Channel channel = ChannelLookup.of(player);
        if (channel == null) {
            return;
        }
        Runnable inject = () -> inject(channel);
        if (channel.eventLoop().inEventLoop()) {
            inject.run();
        } else {
            channel.eventLoop().execute(inject);
        }
    }

    private void inject(Channel channel) {
        if (channel == null || !channel.isOpen()) {
            return;
        }
        try {
            var pipeline = channel.pipeline();
            if (pipeline.get(PipelineNames.HANDLER) != null) {
                pipeline.remove(PipelineNames.HANDLER);
            }
            String encoder = PipelineNames.encoderName(List.copyOf(pipeline.names()));
            if (encoder != null && pipeline.get(encoder) != null) {
                pipeline.addBefore(encoder, PipelineNames.HANDLER, handler);
            } else {
                pipeline.addLast(PipelineNames.HANDLER, handler);
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("Could not inject add_entity patcher into a connection: " + ex.getMessage());
        }
    }

    private void remove(Channel channel) {
        if (channel == null) {
            return;
        }
        Runnable task = () -> {
            try {
                if (channel.pipeline().get(PipelineNames.HANDLER) != null) {
                    channel.pipeline().remove(PipelineNames.HANDLER);
                }
            } catch (Exception ignored) {
            }
        };
        if (channel.eventLoop().inEventLoop()) {
            task.run();
        } else {
            channel.eventLoop().execute(task);
        }
    }

    @ChannelHandler.Sharable
    private static final class FixHandler extends ChannelDuplexHandler {
        private final AddEntityPacketPatcher patcher;

        private FixHandler(AddEntityPacketPatcher patcher) {
            this.patcher = patcher;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            super.write(ctx, patcher.patch(msg), promise);
        }
    }
}

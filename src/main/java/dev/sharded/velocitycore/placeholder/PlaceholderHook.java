package dev.sharded.velocitycore.placeholder;

import dev.sharded.velocitycore.ShardedVelocityCore;
import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PlaceholderHook {

    private PlaceholderHook() {
    }

    public static void register(ShardedVelocityCore plugin) {
        if (plugin.server().getPluginManager().getPlugin("miniplaceholders").isEmpty()) {
            plugin.logger().info("Status placeholders available internally. Install MiniPlaceholders on Velocity and backend servers for hologram support.");
            return;
        }

        try {
            registerWithMiniPlaceholders(plugin);
            plugin.logger().info("Registered ShardedVelocityCore placeholders with MiniPlaceholders.");
        } catch (ReflectiveOperationException exception) {
            plugin.logger().warn("Failed to register MiniPlaceholders expansion.", exception);
        }
    }

    private static void registerWithMiniPlaceholders(ShardedVelocityCore plugin) throws ReflectiveOperationException {
        Class<?> expansionClass = Class.forName("io.github.miniplaceholders.api.Expansion");
        Object builder = expansionClass.getMethod("builder", String.class).invoke(null, "shardedvelocitycore");
        builder.getClass().getMethod("version", String.class).invoke(builder, "1.0.0");
        builder.getClass().getMethod("author", String.class).invoke(builder, "Sharded");

        for (String server : plugin.config().trackedServers()) {
            String tracked = server;
            builder = invokeGlobalPlaceholder(builder, "status_" + tracked, (queue, ctx) ->
                    LegacyText.parse(plugin.statusManager().getStatusPlaceholder(tracked)));
        }

        builder = invokeAudiencePlaceholder(builder, "numberinqueue", (queue, ctx) ->
                LegacyText.parse(String.valueOf(plugin.queueManager().position(playerId((Audience) ctx)))));

        builder = invokeAudiencePlaceholder(builder, "server", (queue, ctx) -> {
            String server = plugin.queueManager().queuedServer(playerId((Audience) ctx)).orElse("");
            return LegacyText.parse(server);
        });

        builder = invokeAudiencePlaceholder(builder, "numberofpeoplewaitinginqueue", (queue, ctx) -> {
            int waiting = plugin.queueManager()
                    .queuedServer(playerId((Audience) ctx))
                    .map(plugin.queueManager()::waitingCount)
                    .orElse(0);
            return LegacyText.parse(String.valueOf(waiting));
        });

        Object expansion = builder.getClass().getMethod("build").invoke(builder);
        Class<?> miniPlaceholders = Class.forName("io.github.miniplaceholders.api.MiniPlaceholders");
        miniPlaceholders.getMethod("registerExpansion", expansionClass).invoke(null, expansion);
    }

    private static Object invokeGlobalPlaceholder(Object builder, String name, BiComponentResolver resolver)
            throws ReflectiveOperationException {
        Class<?> queueClass = Class.forName("io.github.miniplaceholders.api.resolver.ArgumentQueue");
        Class<?> ctxClass = Class.forName("io.github.miniplaceholders.api.resolver.PlaceholderResolverContext");
        Class<?> tagClass = Class.forName("net.kyori.adventure.text.minimessage.tag.Tag");

        Object functional = java.lang.reflect.Proxy.newProxyInstance(
                PlaceholderHook.class.getClassLoader(),
                new Class<?>[] { Class.forName("io.github.miniplaceholders.api.resolver.GlobalPlaceholderResolver") },
                (proxy, method, args) -> {
                    Component component = resolver.resolve(args[0], args[1]);
                    return tagClass.getMethod("selfClosingInserting", Component.class).invoke(null, component);
                }
        );

        return builder.getClass()
                .getMethod("globalPlaceholder", String.class, Class.forName("io.github.miniplaceholders.api.resolver.GlobalPlaceholderResolver"))
                .invoke(builder, name, functional);
    }

    private static Object invokeAudiencePlaceholder(Object builder, String name, BiComponentResolver resolver)
            throws ReflectiveOperationException {
        Class<?> tagClass = Class.forName("net.kyori.adventure.text.minimessage.tag.Tag");

        Object functional = java.lang.reflect.Proxy.newProxyInstance(
                PlaceholderHook.class.getClassLoader(),
                new Class<?>[] { Class.forName("io.github.miniplaceholders.api.resolver.AudiencePlaceholderResolver") },
                (proxy, method, args) -> {
                    Component component = resolver.resolve(args[0], args[1]);
                    return tagClass.getMethod("selfClosingInserting", Component.class).invoke(null, component);
                }
        );

        return builder.getClass()
                .getMethod("audiencePlaceholder", String.class, Class.forName("io.github.miniplaceholders.api.resolver.AudiencePlaceholderResolver"))
                .invoke(builder, name, functional);
    }

    private static UUID playerId(Audience audience) {
        if (audience instanceof com.velocitypowered.api.proxy.Player player) {
            return player.getUniqueId();
        }
        return new UUID(0, 0);
    }

    @FunctionalInterface
    private interface BiComponentResolver {
        Component resolve(Object queue, Object context) throws ReflectiveOperationException;
    }
}

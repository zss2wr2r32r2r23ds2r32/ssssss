package dev.sharded.velocitycore.placeholder;

import dev.sharded.velocitycore.ShardedVelocityCore;
import dev.sharded.velocitycore.util.LegacyText;
import net.kyori.adventure.text.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlaceholderHook {

    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);

    private PlaceholderHook() {
    }

    public static void register(ShardedVelocityCore plugin) {
        if (!isMiniPlaceholdersLoaded(plugin)) {
            return;
        }

        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        try {
            registerWithMiniPlaceholders(plugin);
            plugin.logger().info("Registered ShardedVelocityCore placeholders with MiniPlaceholders.");
        } catch (ReflectiveOperationException exception) {
            REGISTERED.set(false);
            plugin.logger().error("Failed to register MiniPlaceholders expansion. Placeholders will not work.", exception);
        } catch (NoClassDefFoundError error) {
            REGISTERED.set(false);
            plugin.logger().error("MiniPlaceholders API classes missing. Placeholders will not work.", error);
        }
    }

    public static boolean isMiniPlaceholdersLoaded(ShardedVelocityCore plugin) {
        return plugin.server().getPluginManager().getPlugin("miniplaceholders").isPresent();
    }

    private static void registerWithMiniPlaceholders(ShardedVelocityCore plugin) throws ReflectiveOperationException {
        Class<?> expansionClass = Class.forName("io.github.miniplaceholders.api.Expansion");
        Class<?> tagClass = Class.forName("net.kyori.adventure.text.minimessage.tag.Tag");
        Class<?> globalResolverClass = Class.forName("io.github.miniplaceholders.api.resolver.GlobalPlaceholderResolver");
        Class<?> audienceResolverClass = Class.forName("io.github.miniplaceholders.api.resolver.AudiencePlaceholderResolver");

        Object builder = expansionClass.getMethod("builder", String.class).invoke(null, "shardedvelocitycore");
        builder.getClass().getMethod("version", String.class).invoke(builder, "1.0.0");
        builder.getClass().getMethod("author", String.class).invoke(builder, "Sharded");

        for (String server : plugin.config().trackedServers()) {
            String tracked = server;
            builder = addGlobal(builder, globalResolverClass, tagClass, "status_" + tracked, (queue, ctx) ->
                    LegacyText.parse(plugin.statusManager().getStatusPlaceholder(tracked)));
        }

        builder = addGlobal(builder, globalResolverClass, tagClass, "status", (queue, ctx) -> {
            String server = popString(queue);
            return LegacyText.parse(plugin.statusManager().getStatusPlaceholder(
                    server.isEmpty() ? "survival" : server
            ));
        });

        builder = addAudience(builder, audienceResolverClass, tagClass, "numberinqueue", (queue, ctx) ->
                LegacyText.parse(String.valueOf(plugin.queueManager().position(playerId(ctx)))));

        builder = addAudience(builder, audienceResolverClass, tagClass, "server", (queue, ctx) ->
                LegacyText.parse(plugin.queueManager().queuedServer(playerId(ctx)).orElse("")));

        builder = addAudience(builder, audienceResolverClass, tagClass, "numberofpeoplewaitinginqueue", (queue, ctx) -> {
            int waiting = plugin.queueManager()
                    .queuedServer(playerId(ctx))
                    .map(plugin.queueManager()::waitingCount)
                    .orElse(0);
            return LegacyText.parse(String.valueOf(waiting));
        });

        Object expansion = builder.getClass().getMethod("build").invoke(builder);
        expansion.getClass().getMethod("register").invoke(expansion);
    }

    private static Object addGlobal(
            Object builder,
            Class<?> resolverClass,
            Class<?> tagClass,
            String name,
            BiComponentResolver resolver
    ) throws ReflectiveOperationException {
        Object handler = java.lang.reflect.Proxy.newProxyInstance(
                PlaceholderHook.class.getClassLoader(),
                new Class<?>[] { resolverClass },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(proxy, args);
                    }
                    Component component = resolver.resolve(args[0], args[1]);
                    return tagClass.getMethod("selfClosingInserting", Component.class).invoke(null, component);
                }
        );
        return builder.getClass().getMethod("globalPlaceholder", String.class, resolverClass).invoke(builder, name, handler);
    }

    private static Object addAudience(
            Object builder,
            Class<?> resolverClass,
            Class<?> tagClass,
            String name,
            BiComponentResolver resolver
    ) throws ReflectiveOperationException {
        Object handler = java.lang.reflect.Proxy.newProxyInstance(
                PlaceholderHook.class.getClassLoader(),
                new Class<?>[] { resolverClass },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(proxy, args);
                    }
                    Component component = resolver.resolve(args[0], args[1]);
                    return tagClass.getMethod("selfClosingInserting", Component.class).invoke(null, component);
                }
        );
        return builder.getClass().getMethod("audiencePlaceholder", String.class, resolverClass).invoke(builder, name, handler);
    }

    private static String popString(Object queue) throws ReflectiveOperationException {
        if (queue == null || !(boolean) queue.getClass().getMethod("hasNext").invoke(queue)) {
            return "";
        }
        Object argument = queue.getClass().getMethod("pop").invoke(queue);
        Object value = argument.getClass().getMethod("asString").invoke(argument);
        if (value instanceof Optional<?> optional) {
            return optional.map(Object::toString).orElse("");
        }
        return value == null ? "" : value.toString();
    }

    private static UUID playerId(Object context) throws ReflectiveOperationException {
        Object audience = context.getClass().getMethod("audience").invoke(context);
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

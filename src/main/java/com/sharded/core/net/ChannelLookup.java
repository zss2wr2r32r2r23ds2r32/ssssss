package com.sharded.core.net;

import io.netty.channel.Channel;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Resolves a player's Netty channel on Paper (Mojmap runtime).
 */
public final class ChannelLookup {
    private ChannelLookup() {
    }

    public static Channel of(Player player) {
        if (player == null) {
            return null;
        }
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object listener = named(handle, "connection");
            if (listener == null) {
                return null;
            }
            Channel direct = asChannel(named(listener, "channel"));
            if (direct != null) {
                return direct;
            }
            Object connection = named(listener, "connection");
            if (connection == null) {
                connection = listener;
            }
            return asChannel(named(connection, "channel"));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Channel asChannel(Object value) {
        return value instanceof Channel channel ? channel : null;
    }

    private static Object named(Object owner, String name) throws IllegalAccessException {
        Class<?> cursor = owner.getClass();
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (!field.getName().equals(name)) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(owner);
                if (value != null) {
                    return value;
                }
            }
            for (Method method : cursor.getMethods()) {
                if (method.getParameterCount() != 0) {
                    continue;
                }
                String methodName = method.getName();
                if (!methodName.equals(name) && !methodName.equalsIgnoreCase("get" + name)) {
                    continue;
                }
                try {
                    Object value = method.invoke(owner);
                    if (value != null) {
                        return value;
                    }
                } catch (Exception ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }
}

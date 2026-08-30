package dev.sharded.velocitycore.lobby.util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class DisconnectUtil {

    private DisconnectUtil() {
    }

    public static void disconnect(Player player, Component message) {
        if (tryPaperDisconnect(player, message)) {
            return;
        }
        if (tryPlayerDisconnect(player, message)) {
            return;
        }
        if (tryConnectionDisconnect(player, message)) {
            return;
        }
        player.kick(message);
    }

    private static boolean tryPaperDisconnect(Player player, Component message) {
        try {
            Class<?> paperAdventure = Class.forName("io.papermc.paper.adventure.PaperAdventure");
            Method asVanilla = paperAdventure.getMethod("asVanilla", Component.class);
            Object vanilla = asVanilla.invoke(null, message);

            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = findConnection(handle);
            if (connection == null) {
                return false;
            }

            for (Method method : connection.getClass().getMethods()) {
                if (method.getName().equals("disconnect") && method.getParameterCount() == 1) {
                    Class<?> param = method.getParameterTypes()[0];
                    if (param.isInstance(vanilla) || param.isAssignableFrom(vanilla.getClass())) {
                        method.invoke(connection, vanilla);
                        return true;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        return false;
    }

    private static boolean tryPlayerDisconnect(Player player, Component message) {
        try {
            Method disconnect = player.getClass().getMethod("disconnect", Component.class);
            disconnect.invoke(player, message);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static boolean tryConnectionDisconnect(Player player, Component message) {
        try {
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            Object connection = findConnection(handle);
            if (connection == null) {
                return false;
            }
            for (Method method : connection.getClass().getMethods()) {
                if (!method.getName().equals("disconnect") || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (Component.class.isAssignableFrom(param)) {
                    method.invoke(connection, message);
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        return false;
    }

    private static Object findConnection(Object handle) throws ReflectiveOperationException {
        for (String name : new String[]{"connection", "playerConnection", "serverConnection"}) {
            try {
                Field field = handle.getClass().getField(name);
                field.setAccessible(true);
                Object value = field.get(handle);
                if (value != null) {
                    return value;
                }
            } catch (NoSuchFieldException ignored) {
                // Try next field name.
            }
        }
        return null;
    }
}

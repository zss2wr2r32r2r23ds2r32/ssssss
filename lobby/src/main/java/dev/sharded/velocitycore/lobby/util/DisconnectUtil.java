package dev.sharded.velocitycore.lobby.util;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class DisconnectUtil {

    private DisconnectUtil() {
    }

    public static void disconnect(Player player, Component message) {
        if (tryReflectDisconnect(player, message)) {
            return;
        }
        player.kick(message);
    }

    private static boolean tryReflectDisconnect(Player player, Component message) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object handle = getHandle.invoke(player);
            Object connection = handle.getClass().getField("connection").get(handle);
            Method disconnect = connection.getClass().getMethod("disconnect", Component.class);
            disconnect.invoke(connection, message);
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}

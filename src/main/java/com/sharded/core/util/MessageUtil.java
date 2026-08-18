package com.sharded.core.util;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Sends chat or action bar based on module config. */
public final class MessageUtil {

    private MessageUtil() {
    }

    public static void send(ModuleMessages messages, CommandSender to, String key, String... replacements) {
        String msg = messages.raw(key, replacements);
        if (msg.isEmpty()) return;
        if (messages.useActionBar(key) && to instanceof Player player) {
            player.sendActionBar(Text.c(msg));
        } else {
            to.sendMessage(Text.c(msg));
        }
    }

    /** Lightweight message accessor for non-Module classes. */
    public interface ModuleMessages {
        String raw(String key, String... replacements);

        default boolean useActionBar(String key) {
            return false;
        }
    }
}

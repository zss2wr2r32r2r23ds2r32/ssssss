package com.shardedmc.api;

/**
 * Bukkit-style command sender abstraction.
 */
public interface CommandSender {

    String getName();

    void sendMessage(String message);

    boolean hasPermission(String permission);

    boolean isOp();
}

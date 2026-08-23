package com.shardedmc.api;

/**
 * Permission check abstraction for plugin compatibility.
 */
public interface PermissionManager {

    boolean hasPermission(CommandSender sender, String permission);

    void addPermission(String permission);

    void removePermission(String permission);
}

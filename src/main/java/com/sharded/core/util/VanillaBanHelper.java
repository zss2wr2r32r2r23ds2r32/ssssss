package com.sharded.core.util;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;

/** Syncs with Minecraft's built-in banned-players.json / banned-ips.json lists. */
public final class VanillaBanHelper {

    private VanillaBanHelper() {
    }

    @SuppressWarnings("deprecation")
    public static void pardonIp(String ip) {
        if (ip == null || ip.isBlank()) return;
        Bukkit.getBanList(BanList.Type.IP).pardon(ip);
    }

    @SuppressWarnings("deprecation")
    public static void pardonName(String name) {
        if (name == null || name.isBlank()) return;
        Bukkit.getBanList(BanList.Type.NAME).pardon(name);
        try {
            Bukkit.getBanList(BanList.Type.PROFILE).pardon(name);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean isIpBanned(String ip) {
        if (ip == null || ip.isBlank()) return false;
        return Bukkit.getBanList(BanList.Type.IP).isBanned(ip);
    }

    @SuppressWarnings("deprecation")
    public static boolean isNameBanned(String name) {
        if (name == null || name.isBlank()) return false;
        if (Bukkit.getBanList(BanList.Type.NAME).isBanned(name)) return true;
        try {
            return Bukkit.getBanList(BanList.Type.PROFILE).isBanned(name);
        } catch (Exception ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static List<String> vanillaIpBans() {
        List<String> ips = new ArrayList<>();
        for (BanEntry<?> entry : Bukkit.getBanList(BanList.Type.IP).getBanEntries()) {
            String target = entry.getTarget();
            if (target != null && !target.isBlank()) ips.add(target);
        }
        return ips;
    }

    @SuppressWarnings("deprecation")
    public static List<String> vanillaNameBans() {
        List<String> names = new ArrayList<>();
        for (BanEntry<?> entry : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
            String target = entry.getTarget();
            if (target != null && !target.isBlank()) names.add(target);
        }
        return names;
    }
}

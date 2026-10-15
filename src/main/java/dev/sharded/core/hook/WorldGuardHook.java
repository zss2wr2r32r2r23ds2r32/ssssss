package dev.sharded.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class WorldGuardHook {
    private WorldGuardHook() {
    }

    public static boolean isInNamedRegion(Location location, String regionId) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return false;
        }
        try {
            Class<?> wg = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = wg.getMethod("getInstance").invoke(null);
            Object platform = instance.getClass().getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);

            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object adapted = adapter.getMethod("adapt", Location.class).invoke(null, location);
            Object set = query.getClass().getMethod("getApplicableRegions", adapted.getClass()).invoke(query, adapted);
            Iterable<?> regions = (Iterable<?>) set;
            for (Object region : regions) {
                Object id = region.getClass().getMethod("getId").invoke(region);
                if (regionId.equalsIgnoreCase(String.valueOf(id))) {
                    return true;
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return false;
    }
}

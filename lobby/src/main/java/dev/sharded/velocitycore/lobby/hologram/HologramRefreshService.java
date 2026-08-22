package dev.sharded.velocitycore.lobby.hologram;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.logging.Level;

public final class HologramRefreshService {

    private final JavaPlugin plugin;

    public HologramRefreshService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void refreshAll() {
        refreshDecentHolograms();
        refreshHolographicDisplays();
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    private void refreshDecentHolograms() {
        try {
            Class<?> apiClass = Class.forName("eu.decentsoftware.holograms.api.DecentHologramsAPI");
            Object api = apiClass.getMethod("get").invoke(null);
            Object manager = api.getClass().getMethod("getHologramManager").invoke(api);
            Collection<?> holograms = (Collection<?>) manager.getClass().getMethod("getHolograms").invoke(manager);
            for (Object hologram : holograms) {
                invokeFirst(hologram, "updateAll", "update", "realignLines");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.FINE, "DecentHolograms refresh skipped", exception);
        }
    }

    private void refreshHolographicDisplays() {
        try {
            Class<?> apiClass = Class.forName("com.gmail.filoghost.holographicdisplays.api.HologramsAPI");
            Object api = apiClass.getMethod("get", org.bukkit.plugin.Plugin.class).invoke(null, plugin);
            Collection<?> holograms = (Collection<?>) api.getClass().getMethod("getHolograms").invoke(api);
            for (Object hologram : holograms) {
                invokeFirst(hologram, "refresh", "update");
            }
        } catch (ClassNotFoundException ignored) {
        } catch (ReflectiveOperationException exception) {
            plugin.getLogger().log(Level.FINE, "HolographicDisplays refresh skipped", exception);
        }
    }

    private static void invokeFirst(Object target, String... methods) throws ReflectiveOperationException {
        for (String methodName : methods) {
            try {
                Method method = target.getClass().getMethod(methodName);
                method.invoke(target);
                return;
            } catch (NoSuchMethodException ignored) {
            }
        }
    }
}

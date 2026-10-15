package dev.sharded.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class PlaceholderHook {
    private PlaceholderHook() {
    }

    public static String apply(Player player, String text) {
        if (text == null) {
            return "";
        }
        String result = text;
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                Method method = api.getMethod("setPlaceholders", Player.class, String.class);
                Object applied = method.invoke(null, player, result);
                if (applied != null) {
                    result = applied.toString();
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }
        result = result.replace("%player_name%", player.getName());
        result = result.replace("%player%", player.getName());
        if (result.contains("%luckperms_prefix%") || result.contains("%luckperms_suffix%")) {
            LuckPermsBits bits = luckPerms(player);
            result = result.replace("%luckperms_prefix%", bits.prefix);
            result = result.replace("%luckperms_suffix%", bits.suffix);
        }
        return result;
    }

    private static LuckPermsBits luckPerms(Player player) {
        try {
            Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
            Object api = Bukkit.getServicesManager().load(luckPermsClass);
            if (api == null) {
                return LuckPermsBits.EMPTY;
            }
            Method adapter = luckPermsClass.getMethod("getPlayerAdapter", Class.class);
            Object playerAdapter = adapter.invoke(api, Player.class);
            Method getUser = playerAdapter.getClass().getMethod("getUser", Player.class);
            Object user = getUser.invoke(playerAdapter, player);
            Method cached = user.getClass().getMethod("getCachedData");
            Object cachedData = cached.invoke(user);
            Method meta = cachedData.getClass().getMethod("getMetaData");
            Object metaData = meta.invoke(cachedData);
            String prefix = String.valueOf(metaData.getClass().getMethod("getPrefix").invoke(metaData));
            String suffix = String.valueOf(metaData.getClass().getMethod("getSuffix").invoke(metaData));
            if ("null".equals(prefix)) {
                prefix = "";
            }
            if ("null".equals(suffix)) {
                suffix = "";
            }
            return new LuckPermsBits(prefix, suffix);
        } catch (ReflectiveOperationException ignored) {
            return LuckPermsBits.EMPTY;
        }
    }

    private record LuckPermsBits(String prefix, String suffix) {
        private static final LuckPermsBits EMPTY = new LuckPermsBits("", "");
    }
}

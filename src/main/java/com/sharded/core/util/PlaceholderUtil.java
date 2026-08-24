package com.sharded.core.util;

import com.sharded.core.ShardedCore;
import com.sharded.core.modules.koth.KothModule;
import com.sharded.core.modules.outpost.OutpostModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;

/** Resolves ShardedCore placeholders in GUIs, with optional PlaceholderAPI pass-through. */
public final class PlaceholderUtil {

    private PlaceholderUtil() {
    }

    public static String apply(Player player, String input) {
        if (input == null || input.isEmpty()) return "";
        String out = applyInternal(input);
        if (player != null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            out = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, out);
        }
        return out;
    }

    public static java.util.List<String> applyList(Player player, java.util.List<String> lines) {
        java.util.List<String> out = new java.util.ArrayList<>(lines.size());
        for (String line : lines) out.add(apply(player, line));
        return out;
    }

    private static String applyInternal(String input) {
        ShardedCore plugin = ShardedCore.get();
        if (plugin == null) return input;

        OutpostModule outpost = plugin.modules().get(OutpostModule.class);
        long outpostMs = outpost == null ? 0 : outpost.millisUntilStart();
        String outpostTime = TimeFormat.hms(outpostMs);

        KothModule koth = plugin.modules().get(KothModule.class);
        long kothMs = koth == null ? 0 : koth.millisUntilStart();
        String kothTime = TimeFormat.hms(kothMs);

        String out = input
                .replace("%shardedcore_outpost_time%", outpostTime)
                .replace("%shardedcore_outpost_countdown%", outpostTime)
                .replace("%outpost_time%", outpostTime)
                .replace("%shardedcore_koth_time%", kothTime)
                .replace("%shardedcore_koth_countdown%", kothTime)
                .replace("%koth_time%", kothTime);

        if (outpost != null) {
            out = out.replace("%shardedcore_outpost_active%", outpost.isActive() ? "true" : "false")
                    .replace("%shardedcore_outpost_capturer%", outpost.capturerName())
                    .replace("%shardedcore_outpost_percent%",
                            String.format(Locale.US, "%.0f", outpost.capturePercent()));
        }
        if (koth != null) {
            out = out.replace("%shardedcore_koth_active%", koth.isActive() ? "true" : "false")
                    .replace("%shardedcore_koth_leader%", koth.leaderName())
                    .replace("%shardedcore_koth_leader_points%",
                            String.format(Locale.US, "%.0f", koth.leaderPoints()));
        }
        return out;
    }
}
